package com.fragment.policeforce.exam.api.vo;

import com.fragment.policeforce.exam.redis.ExamRedisService;
import lombok.Data;

/**
 * 体检预约受理结果。
 *
 * <p>热门通道返回 {@code PENDING}：Redis 已原子预占成功，
 * 但 MySQL 落库还在异步进行中，客户端用同一个 Idempotency-Key 轮询
 * {@code GET /exam/requests/{requestId}} 就能拿到最终结果。</p>
 */
@Data
public class ExamSubmitVO {

    /**
     * 幂等键（request_id）
     */
    private String requestId;

    /**
     * PENDING / CONFIRMED / REJECTED / WAITING
     */
    private String status;

    /**
     * 体检预约单 ID（确认成功后才有）
     */
    private Long appointmentId;

    /**
     * 体检时段 ID
     */
    private Long slotId;

    /**
     * 体检批次 ID
     */
    private Long batchId;

    /**
     * 提示信息
     */
    private String message;

    public static ExamSubmitVO pending(String requestId, Long slotId, Long batchId) {
        ExamSubmitVO vo = new ExamSubmitVO();
        vo.setRequestId(requestId);
        vo.setStatus("PENDING");
        vo.setSlotId(slotId);
        vo.setBatchId(batchId);
        vo.setMessage("体检预约请求已受理，正在异步确认");
        return vo;
    }

    public static ExamSubmitVO fromState(ExamRedisService.ExamRequestState state) {
        ExamSubmitVO vo = new ExamSubmitVO();
        vo.setRequestId(state.requestId());
        vo.setStatus(state.status());
        vo.setAppointmentId(state.appointmentId());
        vo.setSlotId(state.slotId());
        vo.setBatchId(state.batchId());
        vo.setMessage(state.rejectReason() == null ? "体检预约请求处理中" : state.rejectReason());
        return vo;
    }
}
