package com.fragment.policeforce.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.ExamAppointment;
import com.fragment.policeforce.entity.ExamAppointmentRequest;
import com.fragment.policeforce.entity.ExamBatch;
import com.fragment.policeforce.entity.ExamSlot;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.exam.api.vo.ExamSubmitVO;
import com.fragment.policeforce.exam.model.ExamStatusConstants;
import com.fragment.policeforce.exam.persistence.ExamAppointmentMapper;
import com.fragment.policeforce.exam.persistence.ExamAppointmentRequestMapper;
import com.fragment.policeforce.exam.persistence.ExamSlotMapper;
import com.fragment.policeforce.exam.support.ExamAppointmentNoGenerator;
import com.fragment.policeforce.mapper.ExamBatchMapper;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 普通体检时段的同步事务链路。
 *
 * <p>普通时段没有放号瞬间的惊群，直接走 MySQL 条件扣减即可：
 * {@code UPDATE exam_slot SET remain_quota = remain_quota - 1
 * WHERE id = ? AND remain_quota >= 1}。
 * 受影响行数为 0 就是名额已满，由 InnoDB 行锁保证并发下不会超卖。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NormalExamService {

    private final ExamSlotMapper slotMapper;
    private final ExamBatchMapper batchMapper;
    private final PoliceOfficerMapper officerMapper;
    private final ExamAppointmentMapper appointmentMapper;
    private final ExamAppointmentRequestMapper requestMapper;
    private final ExamEligibilityService eligibilityService;
    private final ExamAppointmentNoGenerator noGenerator;

    /**
     * 受理一次普通体检预约。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamSubmitVO create(String requestId, Long officerId, ExamSlot slot) {
        // 幂等：同一个 request_id 直接返回已有结果。
        ExamAppointmentRequest existing = requestMapper.selectOne(
                new LambdaQueryWrapper<ExamAppointmentRequest>()
                        .eq(ExamAppointmentRequest::getRequestId, requestId)
                        .last("LIMIT 1"));
        if (existing != null) {
            return toResult(existing);
        }

        ExamBatch batch = batchMapper.selectById(slot.getBatchId());
        String batchError = eligibilityService.checkBatch(batch);
        if (batchError != null) {
            throw new BusinessException(409, batchError);
        }
        String slotError = eligibilityService.check(slot.getId(), officerId);
        if (slotError != null) {
            throw new BusinessException(409, slotError);
        }
        if (eligibilityService.hasActiveAppointment(officerId, slot.getId())) {
            throw new BusinessException(409, "该警员已预约该体检时段（一人一档）");
        }

        // 落账本：唯一索引保证并发下只会有一条。
        if (requestMapper.insertProcessingIgnore(requestId, officerId, slot.getId(), slot.getBatchId()) == 0) {
            ExamAppointmentRequest raced = requestMapper.selectOne(
                    new LambdaQueryWrapper<ExamAppointmentRequest>()
                            .eq(ExamAppointmentRequest::getRequestId, requestId)
                            .last("LIMIT 1"));
            if (raced != null) {
                return toResult(raced);
            }
            throw new BusinessException(409, "重复的体检预约请求");
        }

        // 数据库侧防超卖的最后一道约束。
        if (slotMapper.deductQuotaIfAvailable(slot.getId(), 1) == 0) {
            finishRejected(requestId, ExamStatusConstants.REJECT_OUT_OF_STOCK, "体检名额已满，可加入候补队列");
            throw new BusinessException(409, "体检名额已满，可加入候补队列");
        }

        // 落预约单。「一人一档」由唯一键 (officer_id, slot_id, is_active) 兜底。
        ExamAppointment appointment = buildAppointment(requestId, officerId, slot);
        try {
            appointmentMapper.insert(appointment);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            // 并发下同一警员抢到了同一时段的两个名额：还回名额并拒绝。
            slotMapper.restoreQuota(slot.getId(), 1);
            finishRejected(requestId, ExamStatusConstants.REJECT_DUPLICATE_OFFICER,
                    "该警员已预约该体检时段（一人一档）");
            throw new BusinessException(409, "该警员已预约该体检时段（一人一档）");
        }

        ExamAppointmentRequest request = requireRequest(requestId);
        request.setStatus(ExamStatusConstants.CONFIRMED);
        request.setAppointmentId(appointment.getId());
        request.setCompletedAt(LocalDateTime.now());
        requestMapper.updateById(request);

        ExamSubmitVO vo = new ExamSubmitVO();
        vo.setRequestId(requestId);
        vo.setStatus(ExamStatusConstants.CONFIRMED);
        vo.setAppointmentId(appointment.getId());
        vo.setSlotId(slot.getId());
        vo.setBatchId(slot.getBatchId());
        vo.setMessage("体检预约成功");
        return vo;
    }

    private ExamAppointment buildAppointment(String requestId, Long officerId, ExamSlot slot) {
        PoliceOfficer officer = officerMapper.selectById(officerId);
        ExamAppointment appointment = new ExamAppointment();
        appointment.setAppointmentNo(noGenerator.next());
        appointment.setOfficerId(officerId);
        appointment.setSlotId(slot.getId());
        appointment.setBatchId(slot.getBatchId());
        appointment.setOfficerName(officer == null ? null : officer.getName());
        appointment.setSlotStartDatetime(slot.getStartDatetime());
        appointment.setSlotEndDatetime(slot.getEndDatetime());
        appointment.setIsActive(1);
        appointment.setStatus(ExamStatusConstants.BOOKED);
        appointment.setSourceType("NORMAL");
        appointment.setFromWaiting(0);
        appointment.setCreatedAt(LocalDateTime.now());
        appointment.setUpdatedAt(LocalDateTime.now());
        return appointment;
    }

    private void finishRejected(String requestId, String rejectCode, String rejectReason) {
        ExamAppointmentRequest request = requireRequest(requestId);
        request.setStatus(ExamStatusConstants.REJECTED);
        request.setRejectCode(rejectCode);
        request.setRejectReason(rejectReason);
        request.setCompletedAt(LocalDateTime.now());
        requestMapper.updateById(request);
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

    private ExamSubmitVO toResult(ExamAppointmentRequest request) {
        ExamSubmitVO vo = new ExamSubmitVO();
        vo.setRequestId(request.getRequestId());
        vo.setStatus(request.getStatus());
        vo.setAppointmentId(request.getAppointmentId());
        vo.setSlotId(request.getSlotId());
        vo.setBatchId(request.getBatchId());
        vo.setMessage(request.getRejectReason() == null ? "体检预约请求已受理" : request.getRejectReason());
        return vo;
    }
}
