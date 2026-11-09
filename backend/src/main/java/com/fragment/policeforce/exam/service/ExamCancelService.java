package com.fragment.policeforce.exam.service;

import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.common.outbox.MessageOutboxService;
import com.fragment.policeforce.entity.ExamAppointment;
import com.fragment.policeforce.exam.messaging.ExamWaitingPromotionCommand;
import com.fragment.policeforce.exam.persistence.ExamAppointmentMapper;
import com.fragment.policeforce.exam.persistence.ExamSlotMapper;
import com.fragment.policeforce.exam.persistence.ExamWaitingMapper;
import com.fragment.policeforce.exam.redis.ExamRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 体检预约取消服务。
 *
 * <p>取消是「释放名额」链路的起点：
 * <ol>
 *   <li>条件更新把预约单置为 CANCELLED（只有 BOOKED 且未签到才允许取消）；</li>
 *   <li>MySQL 侧还名额（{@code remain_quota + 1}）；</li>
 *   <li>Redis 侧还名额 + 移除警员占位；</li>
 *   <li>发布一条延迟消息，到点后从候补队列 FIFO 递补。</li>
 * </ol>
 *
 * <p>第 4 步用延迟消息而不是立即递补：递补需要走完整的落库流程，
 * 立刻做会让取消接口变慢；而且延迟几秒能让「取消 → 候补递补」这段
 * 在业务上更平滑（用户看到的是名额稍后回到候补队列）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamCancelService {

    private final ExamAppointmentMapper appointmentMapper;
    private final ExamSlotMapper slotMapper;
    private final ExamWaitingMapper waitingMapper;
    private final ExamRedisService examRedis;
    private final MessageOutboxService outboxService;

    @Value("${app.exam.waiting.topic:exam-waiting}")
    private String waitingTopic;

    @Value("${app.exam.waiting.delay-seconds:5}")
    private long waitingDelaySeconds;

    /**
     * 取消体检预约。
     *
     * @param officerId 操作人（警员本人或管理员）
     * @param appointmentId 预约单 ID
     * @param reason 取消原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long officerId, Long appointmentId, String reason) {
        ExamAppointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BusinessException(404, "体检预约单不存在");
        }
        if (officerId != null && !officerId.equals(appointment.getOfficerId())) {
            throw new BusinessException(403, "不能取消他人的体检预约");
        }

        // 条件更新：只有 BOOKED 且未签到才允许取消。
        int updated = appointmentMapper.cancelIfBooked(appointmentId, reason);
        if (updated <= 0) {
            throw new BusinessException(409, "当前体检预约状态不允许取消");
        }

        // 还 MySQL 名额。
        slotMapper.restoreQuota(appointment.getSlotId(), 1);

        // 还 Redis 名额 + 移除警员占位（Redis 侧在提交后执行）。
        examRedis.releaseAfterCancellation(appointment.getSlotId(), appointment.getOfficerId());

        // 候补递补：名额回到池子后，队列里排第一的候补应当补上。
        schedulePromotion(appointment);
    }

    /**
     * 发布候补递补的延迟消息。
     */
    private void schedulePromotion(ExamAppointment appointment) {
        ExamWaitingPromotionCommand command = new ExamWaitingPromotionCommand(
                appointment.getSlotId(), appointment.getBatchId(), appointment.getAppointmentNo());
        outboxService.enqueue(
                "EXAM_WAITING",
                appointment.getSlotId() + ":" + appointment.getId(),
                "EXAM_WAITING_PROMOTION",
                waitingTopic,
                "PROMOTE",
                appointment.getSlotId() + ":" + appointment.getId(),
                LocalDateTime.now().plusSeconds(Math.max(1L, waitingDelaySeconds)),
                command
        );
    }
}
