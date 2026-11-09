package com.fragment.policeforce.exam.redis;

import com.fragment.policeforce.common.exception.BusinessException;

/**
 * Redis 命令可能已经执行，但客户端无法确认结果；只能通过事务消息回查收敛。
 */
public class RedisDecisionUnknownException extends BusinessException {

    public RedisDecisionUnknownException(Throwable cause) {
        super(503, "Redis 预占结果暂时无法确认");
        if (cause != null) {
            initCause(cause);
        }
    }
}
