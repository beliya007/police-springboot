package com.fragment.policeforce.allocation.redis;

import com.fragment.policeforce.common.constants.DutySlotTypeConstants;
import com.fragment.policeforce.common.exception.BusinessException;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AllocationRateLimiter {

    private static final String KEY_PREFIX = "allocation:rate-limit:";

    private static final Duration MINIMUM_KEEP_ALIVE = Duration.ofMinutes(1);

    private final RedissonClient redissonClient;
    private final boolean enabled;
    private final int normalMaxRequests;
    private final int normalWindowSeconds;
    private final int hotMaxRequests;
    private final int hotWindowSeconds;

    public AllocationRateLimiter(RedissonClient redissonClient,
                                  @Value("${app.allocation.rate-limit.enabled:true}") boolean enabled,
                                  @Value("${app.allocation.rate-limit.normal-max-requests:5}") int normalMaxRequests,
                                  @Value("${app.allocation.rate-limit.normal-window-seconds:10}") int normalWindowSeconds,
                                  @Value("${app.allocation.rate-limit.hot-max-requests:3}") int hotMaxRequests,
                                  @Value("${app.allocation.rate-limit.hot-window-seconds:10}") int hotWindowSeconds) {
        this.redissonClient = redissonClient;
        this.enabled = enabled;
        this.normalMaxRequests = normalMaxRequests;
        this.normalWindowSeconds = normalWindowSeconds;
        this.hotMaxRequests = hotMaxRequests;
        this.hotWindowSeconds = hotWindowSeconds;
    }

    public void checkCreateAllocationLimit(Long userId, String slotType) {
        if (!enabled || userId == null) {
            return;
        }

        boolean hotSlot = DutySlotTypeConstants.HOT.equals(slotType);
        int maxRequests = hotSlot ? hotMaxRequests : normalMaxRequests;
        int windowSeconds = hotSlot ? hotWindowSeconds : normalWindowSeconds;

        String key = KEY_PREFIX + userId + ":" + (hotSlot ? "HOT" : "NORMAL")
                + ":" + maxRequests + ":" + windowSeconds;
        Duration interval = Duration.ofSeconds(windowSeconds);
        Duration keepAlive = interval.multipliedBy(6).compareTo(MINIMUM_KEEP_ALIVE) >= 0
                ? interval.multipliedBy(6)
                : MINIMUM_KEEP_ALIVE;

        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.trySetRate(RateType.OVERALL, maxRequests, interval, keepAlive);
        if (!limiter.tryAcquire()) {
            throw new BusinessException(429, hotSlot
                    ? "热门预约请求过于频繁，请稍后再试"
                    : "预约请求过于频繁，请稍后再试");
        }
    }
}
