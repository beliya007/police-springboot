package com.fragment.policeforce.efficiency.messaging;

/**
 * 效能重算结果事件。
 *
 * <p>在 outbox 信封里以 JSON 形式投递，由消费端解析后收敛状态与发通知。</p>
 *
 * @param requestId        幂等键
 * @param batchNo          批次号
 * @param status           CONFIRMED / REJECTED
 * @param affectedStations 受影响警局数
 * @param calcVersion      计算版本
 * @param rejectCode       拒绝码
 * @param rejectReason     拒绝原因
 */
public record RecalcResultEvent(String requestId,
                                String batchNo,
                                String status,
                                Integer affectedStations,
                                Long calcVersion,
                                String rejectCode,
                                String rejectReason) {
}
