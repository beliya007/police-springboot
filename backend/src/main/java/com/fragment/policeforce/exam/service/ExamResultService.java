package com.fragment.policeforce.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.common.audit.AdminAuditHelper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.ExamAppointment;
import com.fragment.policeforce.entity.ExamResult;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.efficiency.service.EfficiencyConstants;
import com.fragment.policeforce.efficiency.service.RecalculationService;
import com.fragment.policeforce.exam.persistence.ExamAppointmentMapper;
import com.fragment.policeforce.exam.redis.ExamRedisService;
import com.fragment.policeforce.mapper.ExamResultMapper;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 体检结论录入服务。
 *
 * <p>这是「体检 → 效能」闭环的入口：录入一条体检结论后，
 * 立即触发受影响警局的<strong>增量重算</strong>（而不是全量重算），
 * 让看板上的实际效能与缺编人数及时反映体检结果。</p>
 *
 * <p>重算走的是和后台调整权重一样的异步链路：202 Accepted + 分片消息 + CAS 收敛，
 * 所以录入结论的接口不会被重算的计算耗时拖住。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamResultService {

    private final ExamResultMapper resultMapper;
    private final ExamAppointmentMapper appointmentMapper;
    private final PoliceOfficerMapper officerMapper;
    private final ExamRedisService examRedis;
    private final RecalculationService recalculationService;
    private final AdminAuditHelper adminAuditHelper;

    /**
     * 录入体检结论。
     *
     * @param appointmentId 体检预约单 ID
     * @param conclusion    结论：PASS / FAIL / INJURED
     * @param note          结论备注
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamResult record(Long appointmentId, String conclusion, String note, String operator) {
        if (appointmentId == null) {
            throw new BusinessException(400, "体检预约单不能为空");
        }
        String normalized = normalizeConclusion(conclusion);

        ExamAppointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BusinessException(404, "体检预约单不存在");
        }

        ExamResult result = new ExamResult();
        result.setAppointmentId(appointmentId);
        result.setOfficerId(appointment.getOfficerId());
        result.setStationId(appointment.getBatchId() == null ? null : stationIdOf(appointment.getOfficerId()));
        result.setModuleId(moduleIdOf(appointment.getOfficerId()));
        result.setConclusion(normalized);
        result.setConclusionNote(note);
        result.setExaminedAt(LocalDateTime.now());
        result.setRecordedBy(operator);
        result.setCreatedAt(LocalDateTime.now());
        result.setUpdatedAt(LocalDateTime.now());

        final Long[] recordedId = new Long[1];
        adminAuditHelper.execute(
                "EXAM_RESULT",
                "RECORD",
                "EXAM_APPOINTMENT",
                () -> appointmentId,
                () -> "appointmentId=" + appointmentId + ", conclusion=" + normalized,
                () -> {
                    resultMapper.insert(result);
                    recordedId[0] = result.getId();
                    // 预约单标记为已完成。
                    appointmentMapper.completeIfBooked(appointmentId);
                }
        );

        // 触发增量重算：只重算该警员所属警局。
        triggerIncrementalRecalculation(appointment.getOfficerId());

        return resultMapper.selectById(recordedId[0]);
    }

    /**
     * 消费体检结果事件：收敛 Redis 状态（把 PRE_RESERVED 推进到终态）。
     *
     * <p>Redis 的 complete 脚本在结果非 CONFIRMED 时会自动释放警员占位并回补库存，
     * 所以这里不需要额外做补偿。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(String eventId, com.fragment.policeforce.exam.messaging.ExamResultEvent event) {
        if (eventId == null || event == null || event.requestId() == null
                || event.officerId() == null || event.slotId() == null || event.status() == null) {
            throw new IllegalArgumentException("Invalid exam result event");
        }
        examRedis.complete(new com.fragment.policeforce.exam.messaging.ExamResultEvent(
                event.requestId(),
                event.officerId(),
                event.slotId(),
                event.batchId(),
                event.status(),
                event.appointmentId(),
                event.waitingId(),
                event.rejectCode(),
                event.rejectReason()
        ));
    }

    /**
     * 查询某警员的最新体检结论。
     */
    public ExamResult latestOf(Long officerId) {
        if (officerId == null) {
            return null;
        }
        return resultMapper.selectOne(new LambdaQueryWrapper<ExamResult>()
                .eq(ExamResult::getOfficerId, officerId)
                .orderByDesc(ExamResult::getId)
                .last("LIMIT 1"));
    }

    /**
     * 触发受影响警局的增量重算。
     *
     * <p>失败不应该影响体检结论的落库（结论已经记完了），
     * 所以这里吞掉异常只记日志，由后台的定时兜底重算来补齐。</p>
     */
    private void triggerIncrementalRecalculation(Long officerId) {
        PoliceOfficer officer = officerMapper.selectById(officerId);
        if (officer == null || officer.getStationId() == null) {
            return;
        }
        try {
            recalculationService.trigger(
                    EfficiencyConstants.TRIGGER_EXAM_RESULT,
                    EfficiencyConstants.SCOPE_STATION,
                    officer.getStationId(),
                    null,
                    null);
        } catch (RuntimeException exception) {
            log.warn("Failed to trigger incremental recalculation after exam result. officerId={}, reason={}",
                    officerId, exception.getMessage());
        }
    }

    private Long stationIdOf(Long officerId) {
        PoliceOfficer officer = officerMapper.selectById(officerId);
        return officer == null ? null : officer.getStationId();
    }

    private Long moduleIdOf(Long officerId) {
        PoliceOfficer officer = officerMapper.selectById(officerId);
        return officer == null ? null : officer.getModuleId();
    }

    private String normalizeConclusion(String conclusion) {
        if (conclusion == null || conclusion.isBlank()) {
            throw new BusinessException(400, "体检结论不能为空");
        }
        String normalized = conclusion.trim().toUpperCase();
        if (!EfficiencyConstants.CONCLUSION_PASS.equals(normalized)
                && !EfficiencyConstants.CONCLUSION_FAIL.equals(normalized)
                && !EfficiencyConstants.CONCLUSION_INJURED.equals(normalized)) {
            throw new BusinessException(400, "体检结论只能是 PASS / FAIL / INJURED");
        }
        return normalized;
    }
}
