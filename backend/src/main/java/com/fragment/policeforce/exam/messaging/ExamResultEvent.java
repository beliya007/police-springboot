package com.fragment.policeforce.exam.messaging;

/**
 * 体检预约落库后的结果事件。
 *
 * <p>由 Outbox 发布，消费端用它收敛 Redis 状态（把 PRE_RESERVED 推进到终态）。</p>
 */
public record ExamResultEvent(
        String requestId,
        Long officerId,
        Long slotId,
        Long batchId,
        String status,
        Long appointmentId,
        Long waitingId,
        String rejectCode,
        String rejectReason
) {
}
