package com.fragment.policeforce.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.common.outbox.MessageOutboxService;
import com.fragment.policeforce.entity.ExamAppointment;
import com.fragment.policeforce.entity.ExamAppointmentRequest;
import com.fragment.policeforce.entity.ExamBatch;
import com.fragment.policeforce.entity.ExamSlot;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.exam.messaging.ExamCreateCommand;
import com.fragment.policeforce.exam.model.ExamStatusConstants;
import com.fragment.policeforce.exam.persistence.ExamAppointmentMapper;
import com.fragment.policeforce.exam.persistence.ExamAppointmentRequestMapper;
import com.fragment.policeforce.exam.persistence.ExamSlotMapper;
import com.fragment.policeforce.exam.support.ExamAppointmentNoGenerator;
import com.fragment.policeforce.mapper.ExamBatchMapper;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 热门体检预约的「确认落库」服务（消费端）。
 *
 * <p>Redis Lua 已经完成了名额裁决，消费端只负责把结果写进 MySQL，
 * 但仍要再校验一遍业务状态：Redis 预占到落库之间存在时间窗口，
 * 管理员可能已经关闭了时段或调整了批次。</p>
 *
 * <p>幂等由请求账本的唯一索引保证：{@code insertProcessingIgnore} 返回 0
 * 就说明这是重复投递，直接安全返回，不做任何写入。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamConfirmationService {

    public static final String RESULT_EVENT_TYPE = "EXAM_RESULT";
    public static final String RESULT_TAG = "RESULT";

    private final ExamAppointmentRequestMapper requestMapper;
    private final ExamAppointmentMapper appointmentMapper;
    private final ExamSlotMapper slotMapper;
    private final ExamBatchMapper batchMapper;
    private final PoliceOfficerMapper officerMapper;
    private final ExamEligibilityService eligibilityService;
    private final ExamAppointmentNoGenerator noGenerator;
    private final MessageOutboxService outboxService;

    @Value("${app.exam.command.result-topic:exam-result}")
    private String resultTopic;

    /**
     * 消费体检预约命令，把它转成 MySQL 里的权威结果（整个方法在一个事务里）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(ExamCreateCommand command) {
        validateCommand(command);

        // 双重幂等的数据库层：账本唯一键去重。
        if (requestMapper.insertProcessingIgnore(command.requestId(), command.officerId(),
                command.slotId(), command.batchId()) == 0) {
            handleDuplicateDelivery(command);
            return;
        }

        ExamSlot slot = slotMapper.selectById(command.slotId());
        ExamBatch batch = slot == null ? null : batchMapper.selectById(slot.getBatchId());

        Rejection rejection = validateBusiness(command, slot, batch);
        if (rejection != null) {
            finish(requireRequest(command.requestId()), ExamStatusConstants.REJECTED,
                    null, null, rejection.code(), rejection.reason());
            return;
        }

        // 一人一档：Redis 里已经挡过一次，这里再挡一次（跨窗口的场景）。
        if (eligibilityService.hasActiveAppointment(command.officerId(), command.slotId())) {
            finish(requireRequest(command.requestId()), ExamStatusConstants.REJECTED,
                    null, null, ExamStatusConstants.REJECT_DUPLICATE_OFFICER,
                    "该警员已预约该体检时段（一人一档）");
            return;
        }

        // MySQL 侧防超卖兜底：Redis 已经扣过了，这里扣的是 MySQL 侧的名额账。
        if (slotMapper.deductQuotaIfAvailable(command.slotId(), 1) == 0) {
            finish(requireRequest(command.requestId()), ExamStatusConstants.REJECTED,
                    null, null, ExamStatusConstants.REJECT_OUT_OF_STOCK, "体检名额已满");
            return;
        }

        ExamAppointment appointment = buildAppointment(command, slot);
        try {
            appointmentMapper.insert(appointment);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            // 并发下同一警员抢到同一时段：还回名额并拒绝。
            slotMapper.restoreQuota(command.slotId(), 1);
            finish(requireRequest(command.requestId()), ExamStatusConstants.REJECTED,
                    null, null, ExamStatusConstants.REJECT_DUPLICATE_OFFICER,
                    "该警员已预约该体检时段（一人一档）");
            return;
        }

        finish(requireRequest(command.requestId()), ExamStatusConstants.CONFIRMED,
                appointment, null, null, null);
    }

    /**
     * 幂等分支：账本已存在，说明这是重复投递。
     */
    private void handleDuplicateDelivery(ExamCreateCommand command) {
        ExamAppointmentRequest existing = requestMapper.selectOne(
                new LambdaQueryWrapper<ExamAppointmentRequest>()
                        .eq(ExamAppointmentRequest::getRequestId, command.requestId())
                        .last("LIMIT 1"));
        if (existing == null) {
            throw new IllegalStateException("Duplicate request key exists but ledger row is missing");
        }
        if (!sameRequest(existing, command)) {
            throw new IllegalArgumentException("Exam command does not match its request ledger");
        }
        if (!isTerminal(existing.getStatus())) {
            throw new IllegalStateException("Exam request is still being processed: " + command.requestId());
        }
    }

    /**
     * 收尾：把账本更新成最终状态，并发出结果事件（经 Outbox 可靠投递）。
     */
    private void finish(ExamAppointmentRequest request,
                        String status,
                        ExamAppointment appointment,
                        Long waitingId,
                        String rejectCode,
                        String rejectReason) {
        request.setStatus(status);
        request.setAppointmentId(appointment == null ? request.getAppointmentId() : appointment.getId());
        if (waitingId != null) {
            request.setWaitingId(waitingId);
        }
        request.setRejectCode(rejectCode);
        request.setRejectReason(rejectReason);
        request.setCompletedAt(LocalDateTime.now());
        if (requestMapper.updateById(request) <= 0) {
            throw new IllegalStateException("Failed to finalize exam request " + request.getRequestId());
        }

        com.fragment.policeforce.exam.messaging.ExamResultEvent event =
                new com.fragment.policeforce.exam.messaging.ExamResultEvent(
                        request.getRequestId(),
                        request.getOfficerId(),
                        request.getSlotId(),
                        request.getBatchId(),
                        status,
                        request.getAppointmentId(),
                        request.getWaitingId(),
                        rejectCode,
                        rejectReason
                );
        outboxService.enqueue(
                "EXAM_APPOINTMENT",
                request.getRequestId(),
                RESULT_EVENT_TYPE,
                resultTopic,
                RESULT_TAG,
                request.getRequestId(),
                LocalDateTime.now(),
                event
        );
    }

    private ExamAppointment buildAppointment(ExamCreateCommand command, ExamSlot slot) {
        PoliceOfficer officer = officerMapper.selectById(command.officerId());
        ExamAppointment appointment = new ExamAppointment();
        appointment.setAppointmentNo(noGenerator.next());
        appointment.setOfficerId(command.officerId());
        appointment.setSlotId(command.slotId());
        appointment.setBatchId(command.batchId());
        appointment.setOfficerName(officer == null ? null : officer.getName());
        appointment.setSlotStartDatetime(slot.getStartDatetime());
        appointment.setSlotEndDatetime(slot.getEndDatetime());
        appointment.setIsActive(1);
        appointment.setStatus(ExamStatusConstants.BOOKED);
        appointment.setSourceType("HOT");
        appointment.setFromWaiting(0);
        appointment.setCreatedAt(LocalDateTime.now());
        appointment.setUpdatedAt(LocalDateTime.now());
        return appointment;
    }

    private Rejection validateBusiness(ExamCreateCommand command, ExamSlot slot, ExamBatch batch) {
        if (slot == null) {
            return new Rejection("SLOT_NOT_FOUND", "体检时段不存在");
        }
        if (!command.batchId().equals(slot.getBatchId())) {
            return new Rejection("SLOT_MISMATCH", "体检时段与批次不匹配");
        }
        String batchError = eligibilityService.checkBatch(batch);
        if (batchError != null) {
            return new Rejection(ExamStatusConstants.REJECT_BATCH_CLOSED, batchError);
        }
        String slotError = eligibilityService.check(slot.getId(), command.officerId());
        if (slotError != null) {
            return new Rejection(ExamStatusConstants.REJECT_SLOT_NOT_BOOKABLE, slotError);
        }
        return null;
    }

    private void validateCommand(ExamCreateCommand command) {
        if (command == null || command.requestId() == null || command.officerId() == null
                || command.slotId() == null || command.batchId() == null) {
            throw new IllegalArgumentException("Invalid exam create command");
        }
    }

    private boolean sameRequest(ExamAppointmentRequest request, ExamCreateCommand command) {
        return request.getOfficerId().equals(command.officerId())
                && request.getSlotId().equals(command.slotId())
                && request.getBatchId().equals(command.batchId());
    }

    private boolean isTerminal(String status) {
        return ExamStatusConstants.CONFIRMED.equals(status)
                || ExamStatusConstants.REJECTED.equals(status)
                || ExamStatusConstants.WAITING.equals(status)
                || ExamStatusConstants.EXPIRED.equals(status);
    }

    private ExamAppointmentRequest requireRequest(String requestId) {
        ExamAppointmentRequest request = requestMapper.selectOne(
                new LambdaQueryWrapper<ExamAppointmentRequest>()
                        .eq(ExamAppointmentRequest::getRequestId, requestId)
                        .last("LIMIT 1"));
        if (request == null) {
            throw new IllegalStateException("Exam request ledger is missing: " + requestId);
        }
        return request;
    }

    private record Rejection(String code, String reason) {
    }
}
