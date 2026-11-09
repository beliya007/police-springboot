package com.fragment.policeforce.exam.messaging;

/**
 * 体检预约命令。
 *
 * <p>在 RocketMQ 事务半消息的「本地事务」阶段，Redis Lua 原子预占成功后，
 * 本命令才会被真正投递到消费端落库。</p>
 */
public record ExamCreateCommand(
        String requestId,
        Long officerId,
        Long slotId,
        Long batchId
) {
}
