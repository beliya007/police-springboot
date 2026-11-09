package com.fragment.policeforce.exam.redis;

import com.fragment.policeforce.common.constants.ExamSlotStatusConstants;
import com.fragment.policeforce.common.constants.ExamSlotTypeConstants;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.ExamSlot;
import com.fragment.policeforce.exam.messaging.ExamResultEvent;
import com.fragment.policeforce.exam.model.ExamStatusConstants;
import com.fragment.policeforce.exam.persistence.ExamAppointmentMapper;
import com.fragment.policeforce.exam.persistence.ExamSlotMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 体检热门时段的 Redis 状态机 + 候补队列。
 *
 * <p>两个职责：</p>
 * <ol>
 *   <li><strong>热门名额裁决</strong>：放号瞬间 500 并发抢 70 个名额，
 *       用 Lua 原子完成「校验 + 扣库存 + 占警员位 + 写请求记录」，
 *       让绝大多数失败请求在 Redis 层就返回，根本不打到 MySQL。</li>
 *   <li><strong>候补队列</strong>：名额抢满后来的请求进入 ZSet 排队，
 *       有人取消释放名额时按 FIFO 递补。ZSet 的 score 是单调递增的入队序号，
 *       保证「先到先得」且在 Redis 重启后可用 {@code exam_waiting} 表重建。</li>
 * </ol>
 *
 * <p>与警力调配通道的差异：体检的互斥维度是「名额」+「一名警员一档」，
 * 所以 users 集合记录的是 officerId 而不是 userId。</p>
 */
@Component
@Slf4j
public class ExamRedisService {

    private static final String SNAPSHOT_PREFIX = "exam:hot:v1:snapshot:";
    private static final String STOCK_PREFIX = "exam:hot:v1:stock:";
    private static final String OFFICERS_PREFIX = "exam:hot:v1:officers:";
    private static final String REQUEST_PREFIX = "exam:request:v1:";
    private static final String WAITING_PREFIX = "exam:waiting:v1:";
    private static final String INIT_LOCK_PREFIX = "lock:exam:hot:v1:init:";

    private static final long ACCEPTED = 0L;
    private static final long OUT_OF_STOCK = 1L;
    private static final long DUPLICATE_OFFICER = 2L;
    private static final long NOT_READY = 3L;
    private static final long SLOT_MISMATCH = 4L;
    private static final long NOT_BOOKABLE = 5L;
    private static final long IDEMPOTENCY_CONFLICT = 6L;
    private static final long IDEMPOTENT_REPLAY = 10L;
    private static final long MIN_CACHE_TTL_MILLIS = 60_000L;

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final ExamSlotMapper examSlotMapper;
    private final ExamAppointmentMapper appointmentMapper;
    private final boolean enabled;
    private final long initWaitMillis;
    private final Duration retentionAfterSlotEnd;
    private final Duration requestRetention;

    private final DefaultRedisScript<Long> initializeScript = initializeScript();
    private final DefaultRedisScript<Long> acceptScript = acceptScript();
    private final DefaultRedisScript<Long> completeScript = completeScript();
    private final DefaultRedisScript<Long> releaseScript = releaseScript();
    private final DefaultRedisScript<Long> enqueueWaitingScript = enqueueWaitingScript();
    private final DefaultRedisScript<Long> promoteWaitingScript = promoteWaitingScript();

    public ExamRedisService(StringRedisTemplate redisTemplate,
                             RedissonClient redissonClient,
                             ExamSlotMapper examSlotMapper,
                             ExamAppointmentMapper appointmentMapper,
                             @Value("${app.exam.hot-redis-enabled:true}") boolean enabled,
                             @Value("${app.exam.hot-init-wait-millis:1000}") long initWaitMillis,
                             @Value("${app.exam.hot-cache-retention-hours:24}") long retentionHours,
                             @Value("${app.exam.hot-request-retention-hours:24}") long requestRetentionHours) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.examSlotMapper = examSlotMapper;
        this.appointmentMapper = appointmentMapper;
        this.enabled = enabled;
        this.initWaitMillis = initWaitMillis;
        this.retentionAfterSlotEnd = Duration.ofHours(Math.max(0, retentionHours));
        this.requestRetention = Duration.ofHours(Math.max(1, requestRetentionHours));
    }

    // ------------------------------------------------------------------
    // 热门名额
    // ------------------------------------------------------------------

    public boolean isPreheatedHotSlot(Long slotId) {
        if (!enabled || slotId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(snapshotKey(slotId)));
        } catch (RuntimeException exception) {
            log.debug("Unable to inspect HOT exam slot snapshot. slotId={}, reason={}",
                    slotId, exception.getMessage());
            return false;
        }
    }

    /**
     * 预占入口：Lua 原子完成校验、扣库存、占警员位、写请求记录。
     */
    public ExamRequestState accept(String requestId, Long officerId, Long slotId, Long batchId) {
        Long result;
        try {
            result = redisTemplate.execute(
                    acceptScript,
                    List.of(snapshotKey(slotId), stockKey(slotId), officersKey(slotId), requestKey(requestId)),
                    requestId,
                    String.valueOf(officerId),
                    String.valueOf(slotId),
                    String.valueOf(batchId),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(requestRetention.toMillis())
            );
        } catch (RuntimeException exception) {
            throw new RedisDecisionUnknownException(exception);
        }
        long outcome = result == null ? NOT_READY : result;
        if (outcome == ACCEPTED) {
            return ExamRequestState.preReserved(requestId, officerId, slotId, batchId);
        }
        if (outcome == IDEMPOTENT_REPLAY) {
            ExamRequestState state = getRequest(requestId);
            if (state != null) {
                return state;
            }
            throw new RedisDecisionUnknownException(null);
        }
        throw rejection(outcome);
    }

    public ExamRequestState getRequest(String requestId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(requestKey(requestId));
        if (values == null || values.isEmpty()) {
            return null;
        }
        return new ExamRequestState(
                text(values, "requestId"),
                number(values, "officerId"),
                number(values, "slotId"),
                number(values, "batchId"),
                text(values, "status"),
                number(values, "appointmentId"),
                number(values, "waitingId"),
                text(values, "rejectCode"),
                text(values, "rejectReason")
        );
    }

    /**
     * 落库完成后收敛 Redis 请求状态。
     */
    public void complete(ExamResultEvent event) {
        Long result = redisTemplate.execute(
                completeScript,
                List.of(stockKey(event.slotId()), officersKey(event.slotId()), requestKey(event.requestId())),
                String.valueOf(event.officerId()),
                event.requestId(),
                event.status(),
                value(event.appointmentId()),
                value(event.waitingId()),
                value(event.rejectCode()),
                value(event.rejectReason()),
                String.valueOf(event.slotId()),
                String.valueOf(requestRetention.toMillis())
        );
        if (result == null) {
            throw unavailable(null);
        }
    }

    /**
     * 用户取消体检预约后释放占位并回补名额。
     */
    public void releaseAfterCancellation(Long slotId, Long officerId) {
        if (!enabled || slotId == null || officerId == null) {
            return;
        }
        runAfterCommit(() -> redisTemplate.execute(
                releaseScript,
                List.of(stockKey(slotId), officersKey(slotId)),
                String.valueOf(officerId)
        ));
    }

    public void syncSlotCache(ExamSlot slot) {
        if (slot == null || slot.getId() == null) {
            return;
        }
        runAfterCommit(() -> {
            deleteSlotCache(slot.getId());
            if (isOpenHotSlot(slot)) {
                initializeIfAbsent(slot);
            }
        });
    }

    public void invalidateSlotCache(Long slotId) {
        if (slotId != null) {
            runAfterCommit(() -> deleteSlotCache(slotId));
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void preheatOpenHotSlots() {
        if (!enabled) {
            return;
        }
        List<ExamSlot> slots = examSlotMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamSlot>()
                        .eq(ExamSlot::getSlotType, ExamSlotTypeConstants.HOT)
                        .eq(ExamSlot::getStatus, ExamSlotStatusConstants.OPEN)
                        .gt(ExamSlot::getEndDatetime, LocalDateTime.now()));
        slots.forEach(slot -> {
            try {
                initializeIfAbsent(slot);
            } catch (RuntimeException exception) {
                log.warn("Failed to preheat HOT exam slot cache. slotId={}", slot.getId(), exception);
            }
        });
    }

    // ------------------------------------------------------------------
    // 候补队列
    // ------------------------------------------------------------------

    /**
     * 把请求放进候补队列。
     *
     * <p>用 ZSet 而不是 List：ZSet 的 score 是单调递增的入队序号，
     * {@code ZPOPMIN} 可以原子地取出最早入队的那个候补，
     * 不需要「先读再删」这种有并发窗口的两步操作。</p>
     *
     * @return 该警员在候补队列中的名次（1-based），已存在则返回原名次
     */
    public Long enqueueWaiting(Long slotId, Long officerId, String requestId) {
        Long sequence = redisTemplate.opsForValue().increment(waitingKey(slotId) + ":seq");
        Long outcome = redisTemplate.execute(
                enqueueWaitingScript,
                List.of(waitingKey(slotId)),
                String.valueOf(officerId),
                String.valueOf(sequence == null ? 1L : sequence)
        );
        if (outcome == null) {
            throw unavailable(null);
        }
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(slotId), officerId.toString());
        return rank == null ? -1L : rank + 1;
    }

    /**
     * 取出候补队列里最早入队的一个。
     *
     * @return 警员 ID，没有则返回 null
     */
    public Long popFirstWaiting(Long slotId) {
        if (slotId == null) {
            return null;
        }
        java.util.Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> popped =
                redisTemplate.opsForZSet().popMin(waitingKey(slotId), 1);
        if (popped == null || popped.isEmpty()) {
            return null;
        }
        org.springframework.data.redis.core.ZSetOperations.TypedTuple<String> head = popped.iterator().next();
        String value = head.getValue();
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 候补队列长度。
     */
    public long waitingCount(Long slotId) {
        Long size = redisTemplate.opsForZSet().zCard(waitingKey(slotId));
        return size == null ? 0L : size;
    }

    /**
     * 移除候补记录（候补过期时调用）。
     */
    public void removeWaiting(Long slotId, Long officerId) {
        if (slotId != null && officerId != null) {
            redisTemplate.opsForZSet().remove(waitingKey(slotId), officerId.toString());
        }
    }

    /**
     * 原子地出队一个候补并把名额预占给它。
     *
     * <p>「出队 + 占名额 + 占警员位」必须在一个 Lua 里完成，
     * 否则两个并发释放事件可能把同一个名额派给两个候补。</p>
     *
     * @return 被递补的警员 ID；没有候补或名额不足时返回 null
     */
    public Long promoteWaiting(Long slotId, Long batchId) {
        Long result = redisTemplate.execute(
                promoteWaitingScript,
                List.of(waitingKey(slotId), stockKey(slotId), officersKey(slotId)),
                String.valueOf(slotId),
                String.valueOf(batchId),
                String.valueOf(System.currentTimeMillis())
        );
        return result == null || result <= 0 ? null : result;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private void initializeIfAbsent(ExamSlot slot) {
        if (!isOpenHotSlot(slot) || isPreheatedHotSlot(slot.getId())) {
            return;
        }
        RLock lock = redissonClient.getLock(INIT_LOCK_PREFIX + slot.getId());
        boolean acquired;
        try {
            acquired = lock.tryLock(initWaitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        }
        if (!acquired) {
            if (!isPreheatedHotSlot(slot.getId())) {
                throw unavailable(null);
            }
            return;
        }
        try {
            if (!isPreheatedHotSlot(slot.getId())) {
                loadCache(slot);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void loadCache(ExamSlot slot) {
        List<Long> bookedOfficers = appointmentMapper.selectActiveBySlot(slot.getId()).stream()
                .map(com.fragment.policeforce.entity.ExamAppointment::getOfficerId)
                .toList();

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(slot.getBatchId()));
        args.add(slot.getStatus());
        args.add(String.valueOf(epochMillis(slot.getOpenTime())));
        args.add(String.valueOf(epochMillis(slot.getEndDatetime())));
        args.add(String.valueOf(slot.getRemainQuota()));
        args.add(String.valueOf(cacheTtlMillis(slot)));
        bookedOfficers.forEach(officerId -> args.add(String.valueOf(officerId)));

        Long result = redisTemplate.execute(
                initializeScript,
                List.of(snapshotKey(slot.getId()), stockKey(slot.getId()), officersKey(slot.getId())),
                args.toArray()
        );
        if (result == null) {
            throw unavailable(null);
        }
    }

    private BusinessException rejection(long result) {
        if (result == OUT_OF_STOCK) {
            return new BusinessException(409, "体检名额已满，可加入候补队列");
        }
        if (result == DUPLICATE_OFFICER) {
            return new BusinessException(409, "该警员已预约该体检时段（一人一档）");
        }
        if (result == SLOT_MISMATCH) {
            return new BusinessException(400, "体检时段与批次不匹配");
        }
        if (result == NOT_BOOKABLE) {
            return new BusinessException(409, "体检时段当前不可预约");
        }
        if (result == IDEMPOTENCY_CONFLICT) {
            return new BusinessException(409, "Idempotency-Key 已用于其他体检请求");
        }
        return unavailable(null);
    }

    private boolean isOpenHotSlot(ExamSlot slot) {
        return slot != null
                && ExamSlotTypeConstants.HOT.equals(slot.getSlotType())
                && ExamSlotStatusConstants.OPEN.equals(slot.getStatus())
                && slot.getEndDatetime() != null
                && slot.getEndDatetime().isAfter(LocalDateTime.now());
    }

    private long cacheTtlMillis(ExamSlot slot) {
        long expiry = epochMillis(slot.getEndDatetime()) + retentionAfterSlotEnd.toMillis();
        return Math.max(expiry - System.currentTimeMillis(), MIN_CACHE_TTL_MILLIS);
    }

    private long epochMillis(LocalDateTime value) {
        return value == null ? -1L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void deleteSlotCache(Long slotId) {
        redisTemplate.delete(List.of(snapshotKey(slotId), stockKey(slotId), officersKey(slotId)));
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private BusinessException unavailable(Throwable cause) {
        BusinessException exception = new BusinessException(503, "体检预约通道暂时不可用，请稍后重试");
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    /**
     * 下一个入队序号。用 Redis 的 INCR 保证单调递增且不重复。
     */
    private double nextSequence(Long slotId) {
        Long value = redisTemplate.opsForValue().increment(waitingKey(slotId) + ":seq");
        return value == null ? 1.0d : value.doubleValue();
    }


    private String snapshotKey(Long slotId) {
        return SNAPSHOT_PREFIX + slotId;
    }

    private String stockKey(Long slotId) {
        return STOCK_PREFIX + slotId;
    }

    private String officersKey(Long slotId) {
        return OFFICERS_PREFIX + slotId;
    }

    private String requestKey(String requestId) {
        return REQUEST_PREFIX + requestId;
    }

    private String waitingKey(Long slotId) {
        return WAITING_PREFIX + slotId;
    }

    private String text(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private Long number(Map<Object, Object> values, String key) {
        String value = text(values, key);
        return value == null ? null : Long.valueOf(value);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    // ------------------------------------------------------------------
    // Lua 脚本
    // ------------------------------------------------------------------

    /**
     * 预热脚本：把 MySQL 的体检名额初始化成 Redis 的三把 key。
     *
     * KEYS[1]=snapshot KEYS[2]=stock KEYS[3]=officers
     * ARGV[1]=batchId ARGV[2]=status ARGV[3]=openAtMillis ARGV[4]=endAtMillis
     * ARGV[5]=remainQuota ARGV[6]=ttlMillis ARGV[7..]=已预约的 officerId
     */
    private DefaultRedisScript<Long> initializeScript() {
        return script("""
                if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
                redis.call('DEL', KEYS[2], KEYS[3])
                redis.call('HSET', KEYS[1],
                    'batchId', ARGV[1], 'status', ARGV[2],
                    'openAtMillis', ARGV[3], 'endAtMillis', ARGV[4])
                redis.call('PEXPIRE', KEYS[1], ARGV[6])
                redis.call('SET', KEYS[2], ARGV[5], 'PX', ARGV[6])
                for index = 7, #ARGV do
                    redis.call('HSET', KEYS[3], ARGV[index], 'BOOKED')
                end
                if #ARGV >= 7 then redis.call('PEXPIRE', KEYS[3], ARGV[6]) end
                return 1
                """);
    }

    /**
     * 预占脚本：原子完成「幂等校验 + 快照校验 + 时间窗校验 + 一人一档校验 + 扣库存 + 占位」。
     *
     * KEYS[1]=snapshot KEYS[2]=stock KEYS[3]=officers KEYS[4]=request
     * ARGV[1]=requestId ARGV[2]=officerId ARGV[3]=slotId ARGV[4]=batchId
     * ARGV[5]=now ARGV[6]=requestTtl
     *
     * 返回：0=成功 1=名额不足 2=该警员已占 3=快照未预热 4=批次不匹配
     *       5=不可预约 6=幂等键冲突 10=完全相同的重放
     */
    private DefaultRedisScript<Long> acceptScript() {
        return script("""
                if redis.call('EXISTS', KEYS[4]) == 1 then
                    if redis.call('HGET', KEYS[4], 'officerId') ~= ARGV[2]
                        or redis.call('HGET', KEYS[4], 'slotId') ~= ARGV[3]
                        or redis.call('HGET', KEYS[4], 'batchId') ~= ARGV[4] then
                        return 6
                    end
                    return 10
                end
                if redis.call('EXISTS', KEYS[1]) == 0 or redis.call('EXISTS', KEYS[2]) == 0 then
                    return 3
                end
                if redis.call('HGET', KEYS[1], 'batchId') ~= ARGV[4] then return 4 end
                if redis.call('HGET', KEYS[1], 'status') ~= 'OPEN' then return 5 end
                local now = tonumber(ARGV[5])
                local openAt = tonumber(redis.call('HGET', KEYS[1], 'openAtMillis') or '-1')
                local endAt = tonumber(redis.call('HGET', KEYS[1], 'endAtMillis') or '-1')
                if openAt < 0 or endAt < 0 or now < openAt or now >= endAt then return 5 end
                if redis.call('HEXISTS', KEYS[3], ARGV[2]) == 1 then return 2 end
                local stock = tonumber(redis.call('GET', KEYS[2]) or '-1')
                if stock <= 0 then return 1 end

                redis.call('DECR', KEYS[2])
                redis.call('HSET', KEYS[3], ARGV[2], ARGV[1])
                local stockTtl = redis.call('PTTL', KEYS[2])
                if stockTtl > 0 then redis.call('PEXPIRE', KEYS[3], stockTtl) end
                redis.call('HSET', KEYS[4],
                    'requestId', ARGV[1], 'officerId', ARGV[2],
                    'slotId', ARGV[3], 'batchId', ARGV[4],
                    'status', 'PRE_RESERVED', 'createdAt', ARGV[5])
                redis.call('PEXPIRE', KEYS[4], ARGV[6])
                return 0
                """);
    }

    /**
     * 收敛脚本：把请求 hash 从 PRE_RESERVED 推进到终态。
     * 非 CONFIRMED 的终态会释放占位并回补库存。
     *
     * KEYS[1]=stock KEYS[2]=officers KEYS[3]=request
     * ARGV[1]=officerId ARGV[2]=requestId ARGV[3]=status ARGV[4]=appointmentId
     * ARGV[5]=waitingId ARGV[6]=rejectCode ARGV[7]=rejectReason
     * ARGV[8]=slotId ARGV[9]=requestTtl
     */
    private DefaultRedisScript<Long> completeScript() {
        return script("""
                local current = redis.call('HGET', KEYS[3], 'status')
                if current == 'CONFIRMED' or current == 'REJECTED' or current == 'WAITING' then
                    return 0
                end
                if ARGV[3] ~= 'CONFIRMED' then
                    local ownerRequest = redis.call('HGET', KEYS[2], ARGV[1])
                    if ownerRequest == ARGV[2] then
                        redis.call('HDEL', KEYS[2], ARGV[1])
                        if redis.call('EXISTS', KEYS[1]) == 1 then redis.call('INCR', KEYS[1]) end
                    end
                end
                redis.call('HSET', KEYS[3], 'status', ARGV[3],
                    'requestId', ARGV[2], 'officerId', ARGV[1],
                    'appointmentId', ARGV[4], 'waitingId', ARGV[5],
                    'rejectCode', ARGV[6], 'rejectReason', ARGV[7], 'slotId', ARGV[8])
                redis.call('PEXPIRE', KEYS[3], ARGV[9])
                return 1
                """);
    }

    /**
     * 取消回补脚本：移除警员占位并把名额 +1。
     */
    private DefaultRedisScript<Long> releaseScript() {
        return script("""
                if redis.call('HDEL', KEYS[2], ARGV[1]) == 1 then
                    if redis.call('EXISTS', KEYS[1]) == 1 then redis.call('INCR', KEYS[1]) end
                    return 1
                end
                return 0
                """);
    }

    /**
     * 入队脚本：幂等地把警员放进候补 ZSet。
     *
     * KEYS[1]=waiting ZSet
     * ARGV[1]=officerId ARGV[2]=sequence
     *
     * 返回：1=新入队 0=已在队列中（幂等）
     */
    private DefaultRedisScript<Long> enqueueWaitingScript() {
        return script("""
                if redis.call('ZSCORE', KEYS[1], ARGV[1]) ~= false then
                    return 0
                end
                redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
                return 1
                """);
    }

    /**
     * 递补脚本：原子完成「出队 + 扣库存 + 占警员位」。
     *
     * KEYS[1]=waiting ZSet KEYS[2]=stock KEYS[3]=officers
     * ARGV[1]=slotId ARGV[2]=batchId ARGV[3]=now
     *
     * 返回：被递补的 officerId；0 表示没有候补或名额不足
     */
    private DefaultRedisScript<Long> promoteWaitingScript() {
        return script("""
                -- 没有候补 → 返回 0
                local candidates = redis.call('ZRANGE', KEYS[1], 0, 0)
                if #candidates == 0 then return 0 end
                -- 名额不足 → 不出队（候补继续排队，等下次释放）
                local stock = tonumber(redis.call('GET', KEYS[2]) or '-1')
                if stock <= 0 then return 0 end
                -- 逐个检查候补，跳过已经预约过的（脏数据）
                for index = 1, 10 do
                    local head = redis.call('ZRANGE', KEYS[1], 0, 0)
                    if #head == 0 then return 0 end
                    local officerId = head[1]
                    redis.call('ZREM', KEYS[1], officerId)
                    if redis.call('HEXISTS', KEYS[3], officerId) == 0 then
                        redis.call('DECR', KEYS[2])
                        redis.call('HSET', KEYS[3], officerId, 'PROMOTED')
                        local stockTtl = redis.call('PTTL', KEYS[2])
                        if stockTtl > 0 then redis.call('PEXPIRE', KEYS[3], stockTtl) end
                        return tonumber(officerId)
                    end
                    -- 该警员已有预约 → 丢弃这条脏候补，继续看下一位
                end
                return 0
                """);
    }

    private DefaultRedisScript<Long> script(String text) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(text);
        return script;
    }

    /**
     * 体检请求在 Redis 中的状态。
     */
    public record ExamRequestState(String requestId,
                                   Long officerId,
                                   Long slotId,
                                   Long batchId,
                                   String status,
                                   Long appointmentId,
                                   Long waitingId,
                                   String rejectCode,
                                   String rejectReason) {

        static ExamRequestState preReserved(String requestId, Long officerId, Long slotId, Long batchId) {
            return new ExamRequestState(requestId, officerId, slotId, batchId,
                    "PRE_RESERVED", null, null, null, null);
        }
    }
}
