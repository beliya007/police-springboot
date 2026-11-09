package com.fragment.policeforce.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.ExamAppointment;
import com.fragment.policeforce.entity.ExamAppointmentRequest;
import com.fragment.policeforce.entity.ExamBatch;
import com.fragment.policeforce.entity.ExamSlot;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.exam.api.vo.ExamSubmitVO;
import com.fragment.policeforce.exam.messaging.ExamCreateCommand;
import com.fragment.policeforce.exam.messaging.ExamCommandPublisher;
import com.fragment.policeforce.exam.model.ExamStatusConstants;
import com.fragment.policeforce.exam.persistence.ExamAppointmentMapper;
import com.fragment.policeforce.exam.persistence.ExamAppointmentRequestMapper;
import com.fragment.policeforce.exam.persistence.ExamSlotMapper;
import com.fragment.policeforce.exam.redis.ExamRedisService;
import com.fragment.policeforce.mapper.ExamBatchMapper;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 体检预约受理入口。
 *
 * <p>两条通道：</p>
 * <ol>
 *   <li><strong>热门通道</strong>（HOT 时段）：请求线程走 RocketMQ 事务半消息，
 *       本地事务里由 Redis Lua 原子预占名额。500 并发抢 70 个名额时，
 *       430 个失败请求在 Redis 层就被判定并直接回滚半消息，
 *       <strong>不会给 MySQL 造成任何写压力</strong>。</li>
 *   <li><strong>普通通道</strong>（NORMAL 时段）：直接走 MySQL 条件扣减
 *       {@code UPDATE ... WHERE remain_quota >= need}，由 InnoDB 行锁保证不超卖。</li>
 * </ol>
 *
 * <p>两条通道都通过 {@code request_id} 唯一索引做幂等：客户端重试不会产生重复预约。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamAppointmentService {

    private final ExamCommandPublisher commandPublisher;
    private final ExamRedisService examRedis;
    private final ExamAppointmentRequestMapper requestMapper;
    private final ExamAppointmentMapper appointmentMapper;
    private final ExamSlotMapper slotMapper;
    private final ExamBatchMapper batchMapper;
    private final PoliceOfficerMapper officerMapper;
    private final ExamEligibilityService eligibilityService;
    private final NormalExamService normalExamService;

    /**
     * 受理一次体检预约请求。
     *
     * @param idempotencyKey 客户端幂等键（UUID）
     * @param officerId      警员 ID
     * @param slotId         体检时段 ID
     */
    public ExamSubmitVO create(String idempotencyKey, Long officerId, Long slotId) {
        String requestId = normalizeRequestId(idempotencyKey);

        ExamSlot slot = resolveSlot(slotId);

        // 热门时段：走 Redis 前置裁决 + 事务半消息
        if (eligibilityService.isHot(slot)) {
            return createHot(requestId, officerId, slot);
        }
        return normalExamService.create(requestId, officerId, slot);
    }

    /**
     * 热门通道受理。
     */
    private ExamSubmitVO createHot(String requestId, Long officerId, ExamSlot slot) {
        // 前置校验：批次放号窗口、警员状态、时段状态。
        // 这一步是「快速失败」的闸门，避免明显无效的请求消耗 Redis 与 MQ 资源。
        ExamBatch batch = batchMapper.selectById(slot.getBatchId());
        String batchError = eligibilityService.checkBatch(batch);
        if (batchError != null) {
            throw new BusinessException(409, batchError);
        }
        String slotError = eligibilityService.check(slot.getId(), officerId);
        if (slotError != null) {
            throw new BusinessException(409, slotError);
        }

        // 发布事务半消息：本地事务里做 Redis Lua 原子预占。
        ExamCreateCommand command = new ExamCreateCommand(requestId, officerId, slot.getId(), slot.getBatchId());
        ExamRedisService.ExamRequestState state = commandPublisher.publish(command);

        return ExamSubmitVO.pending(requestId, state.slotId(), state.batchId());
    }

    /**
     * 查询本人体检预约请求的最终结果。
     *
     * <p>查询顺序：先查 MySQL 账本（权威结果），查不到再查 Redis
     * （可能预占成功但落库还没完成）。</p>
     */
    public ExamSubmitVO getRequest(Long officerId, String rawRequestId) {
        String requestId = normalizeRequestId(rawRequestId);

        ExamAppointmentRequest request = requestMapper.selectOne(
                new LambdaQueryWrapper<ExamAppointmentRequest>()
                        .eq(ExamAppointmentRequest::getRequestId, requestId)
                        .last("LIMIT 1"));
        if (request != null) {
            assertOwner(officerId, request.getOfficerId());
            return toResult(request);
        }

        ExamRedisService.ExamRequestState state = examRedis.getRequest(requestId);
        if (state == null) {
            throw new BusinessException(404, "体检预约请求不存在");
        }
        assertOwner(officerId, state.officerId());
        return ExamSubmitVO.fromState(state);
    }

    /**
     * 归一化幂等键：必须是 UUID。
     */
    public String normalizeRequestId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, "缺少 Idempotency-Key 请求头");
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "Idempotency-Key 必须是 UUID");
        }
    }

    private ExamSlot resolveSlot(Long slotId) {
        if (slotId == null) {
            throw new BusinessException(400, "体检时段不能为空");
        }
        ExamSlot slot = slotMapper.selectById(slotId);
        if (slot == null) {
            throw new BusinessException(404, "体检时段不存在");
        }
        if (eligibilityService.isHot(slot) && !examRedis.isPreheatedHotSlot(slotId)) {
            // 热门时段未预热时不允许穿透到 MySQL 高并发路径：
            // 否则 500 并发会全部压到同一行上，把数据库打满。
            throw new BusinessException(503, "体检时段尚未完成预热，请稍后重试");
        }
        return slot;
    }

    private void assertOwner(Long actorId, Long ownerId) {
        if (actorId == null || !actorId.equals(ownerId)) {
            throw new BusinessException(403, "无权查询其他警员的体检预约请求");
        }
    }

    private ExamSubmitVO toResult(ExamAppointmentRequest request) {
        ExamSubmitVO vo = new ExamSubmitVO();
        vo.setRequestId(request.getRequestId());
        vo.setStatus(apiStatus(request.getStatus()));
        vo.setAppointmentId(request.getAppointmentId());
        vo.setSlotId(request.getSlotId());
        vo.setBatchId(request.getBatchId());
        vo.setMessage(message(request.getStatus(), request.getRejectReason()));
        return vo;
    }

    private String apiStatus(String status) {
        if ("PRE_RESERVED".equals(status) || ExamStatusConstants.PROCESSING.equals(status)) {
            return "PENDING";
        }
        return status;
    }

    private String message(String status, String rejectReason) {
        return switch (apiStatus(status)) {
            case "PENDING" -> "体检预约请求已受理，正在异步确认";
            case ExamStatusConstants.CONFIRMED -> "体检预约成功";
            case ExamStatusConstants.WAITING -> "体检名额已满，已加入候补队列";
            case ExamStatusConstants.REJECTED -> rejectReason == null ? "体检预约未通过最终确认" : rejectReason;
            default -> "体检预约请求状态未知";
        };
    }

    /**
     * 保留对警员查询的引用，便于后续扩展（如查询警员的预约历史）。
     */
    PoliceOfficer requireOfficer(Long officerId) {
        PoliceOfficer officer = officerMapper.selectById(officerId);
        if (officer == null) {
            throw new BusinessException(404, "警员不存在");
        }
        return officer;
    }

    /**
     * 保留对预约单查询的引用。
     */
    ExamAppointment findAppointment(Long appointmentId) {
        return appointmentId == null ? null : appointmentMapper.selectById(appointmentId);
    }

    LocalDateTime now() {
        return LocalDateTime.now();
    }
}
