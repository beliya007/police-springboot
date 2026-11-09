package com.fragment.policeforce.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.ExamAppointment;
import com.fragment.policeforce.entity.ExamSlot;
import com.fragment.policeforce.entity.ExamWaiting;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.exam.messaging.ExamWaitingPromotionCommand;
import com.fragment.policeforce.exam.model.ExamStatusConstants;
import com.fragment.policeforce.exam.persistence.ExamAppointmentMapper;
import com.fragment.policeforce.exam.persistence.ExamSlotMapper;
import com.fragment.policeforce.exam.persistence.ExamWaitingMapper;
import com.fragment.policeforce.exam.redis.ExamRedisService;
import com.fragment.policeforce.exam.support.ExamAppointmentNoGenerator;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 体检候补队列服务。
 *
 * <p>候补队列的实时排序由 Redis ZSet 承担（score = 单调递增的入队序号），
 * 本表 {@code exam_waiting} 是持久化快照，负责三件事：
 * <ol>
 *   <li>入队时落一条 WAITING 记录，靠 {@code (slot_id, officer_id)} 唯一键保证一人一条；</li>
 *   <li>递补时用条件更新 {@code WHERE status = 'WAITING'} 保证只递补一次；</li>
 *   <li>Redis 不可用时可以从这张表重建 ZSet。</li>
 * </ol>
 *
 * <p>递补本身是「Redis 原子出队 + 扣名额 + 占警员位」，一次 Lua 完成，
 * 避免两个并发的释放事件把同一个名额派给两个候补。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamWaitingService {

    private final ExamRedisService examRedis;
    private final ExamWaitingMapper waitingMapper;
    private final ExamAppointmentMapper appointmentMapper;
    private final ExamSlotMapper slotMapper;
    private final PoliceOfficerMapper officerMapper;
    private final ExamAppointmentNoGenerator noGenerator;

    /**
     * 把警员放进候补队列。
     *
     * @return 该警员在队列中的名次（1-based）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long enqueue(Long slotId, Long batchId, Long officerId, String requestId) {
        ExamSlot slot = slotMapper.selectById(slotId);
        if (slot == null) {
            throw new BusinessException(404, "体检时段不存在");
        }

        // 幂等：同一警员在同一时段只有一条候补记录。
        ExamWaiting existing = waitingMapper.selectOne(new LambdaQueryWrapper<ExamWaiting>()
                .eq(ExamWaiting::getSlotId, slotId)
                .eq(ExamWaiting::getOfficerId, officerId)
                .last("LIMIT 1"));
        if (existing != null && ExamStatusConstants.WAITING.equals(existing.getStatus())) {
            return examRedis.enqueueWaiting(slotId, officerId, requestId);
        }

        if (existing == null) {
            ExamWaiting waiting = new ExamWaiting();
            waiting.setSlotId(slotId);
            waiting.setBatchId(batchId);
            waiting.setOfficerId(officerId);
            waiting.setRequestId(requestId);
            waiting.setStatus(ExamStatusConstants.WAITING);
            waiting.setCreatedAt(LocalDateTime.now());
            waiting.setUpdatedAt(LocalDateTime.now());
            waitingMapper.insert(waiting);
        } else {
            existing.setStatus(ExamStatusConstants.WAITING);
            existing.setRequestId(requestId);
            existing.setUpdatedAt(LocalDateTime.now());
            waitingMapper.updateById(existing);
        }

        return examRedis.enqueueWaiting(slotId, officerId, requestId);
    }

    /**
     * 递补：有名额释放时调用。
     *
     * <p>Redis Lua 原子完成「出队 + 扣库存 + 占警员位」，返回被递补的警员 ID；
     * 随后在 MySQL 里落预约单。如果落库失败（唯一键冲突等），
     * Redis 上的名额会由 {@link #rollbackPromotion} 归还。</p>
     *
     * @return 被成功递补的警员 ID；没有候补或名额不足时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Long promote(ExamWaitingPromotionCommand command) {
        Long slotId = command.slotId();
        Long batchId = command.batchId();

        Long officerId = examRedis.promoteWaiting(slotId, batchId);
        if (officerId == null) {
            log.debug("No waiting candidate to promote. slotId={}", slotId);
            return null;
        }

        ExamWaiting waiting = waitingMapper.selectOne(new LambdaQueryWrapper<ExamWaiting>()
                .eq(ExamWaiting::getSlotId, slotId)
                .eq(ExamWaiting::getOfficerId, officerId)
                .last("LIMIT 1"));
        if (waiting == null) {
            log.warn("Waiting record is missing for promoted officer. slotId={}, officerId={}", slotId, officerId);
            rollbackPromotion(slotId, officerId);
            return null;
        }

        // 幂等第二道：只有仍在排队的记录才能被递补。
        if (waitingMapper.promoteIfWaiting(waiting.getId()) == 0) {
            log.info("Waiting record was already promoted. slotId={}, officerId={}", slotId, officerId);
            rollbackPromotion(slotId, officerId);
            return null;
        }

        ExamSlot slot = slotMapper.selectById(slotId);
        if (slot == null) {
            throw new IllegalStateException("Exam slot no longer exists: " + slotId);
        }

        // MySQL 侧扣名额：Redis 已经扣过，这里扣的是 MySQL 的账。
        if (slotMapper.deductQuotaIfAvailable(slotId, 1) == 0) {
            waitingMapper.expireIfWaiting(waiting.getId());
            rollbackPromotion(slotId, officerId);
            return null;
        }

        PoliceOfficer officer = officerMapper.selectById(officerId);
        ExamAppointment appointment = new ExamAppointment();
        appointment.setAppointmentNo(noGenerator.next());
        appointment.setOfficerId(officerId);
        appointment.setSlotId(slotId);
        appointment.setBatchId(batchId);
        appointment.setOfficerName(officer == null ? null : officer.getName());
        appointment.setSlotStartDatetime(slot.getStartDatetime());
        appointment.setSlotEndDatetime(slot.getEndDatetime());
        appointment.setIsActive(1);
        appointment.setStatus(ExamStatusConstants.BOOKED);
        appointment.setSourceType("HOT");
        appointment.setFromWaiting(1);
        appointment.setCreatedAt(LocalDateTime.now());
        appointment.setUpdatedAt(LocalDateTime.now());

        try {
            appointmentMapper.insert(appointment);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            // 该警员已经有预约了，还回名额并让这条候补过期。
            slotMapper.restoreQuota(slotId, 1);
            waitingMapper.expireIfWaiting(waiting.getId());
            rollbackPromotion(slotId, officerId);
            return null;
        }

        log.info("Waiting candidate promoted. slotId={}, officerId={}, appointmentId={}",
                slotId, officerId, appointment.getId());
        return officerId;
    }

    /**
     * 递补失败回滚：把 Redis 上的名额还回去。
     */
    private void rollbackPromotion(Long slotId, Long officerId) {
        examRedis.releaseAfterCancellation(slotId, officerId);
    }

    /**
     * 候补队列长度。
     */
    public long waitingCount(Long slotId) {
        return examRedis.waitingCount(slotId);
    }

    /**
     * 候补过期（比如时段已结束）。
     */
    public void expire(Long waitingId) {
        waitingMapper.expireIfWaiting(waitingId);
    }
}
