package com.fragment.policeforce.exam.messaging;

/**
 * 体检候补递补命令。
 *
 * <p>有名额被释放时发布一条延迟消息，消费者到点后从 Redis ZSet 里
 * 按 FIFO 取出最早的候补并尝试原子预占。</p>
 */
public record ExamWaitingPromotionCommand(
        Long slotId,
        Long batchId,
        String requestId
) {
}
