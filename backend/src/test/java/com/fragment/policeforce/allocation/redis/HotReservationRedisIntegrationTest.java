package com.fragment.policeforce.allocation.redis;

import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.allocation.messaging.AllocationResultEvent;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.mapper.DutySlotMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_INTEGRATION_TESTS", matches = "true")
class HotAllocationRedisIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration("127.0.0.1", 6379);
        configuration.setDatabase(15);
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void luaStateMachineShouldKeepIdempotencyAndRestoreRejectedStockOnlyOnce() {
        long slotId = System.currentTimeMillis();
        long stationId = slotId + 1;
        long userId = slotId + 2;
        long officerId = slotId + 3;
        String requestId = UUID.randomUUID().toString();
        String snapshotKey = "allocation:hot:v2:snapshot:" + slotId;
        String stockKey = "allocation:hot:v2:stock:" + slotId;
        String usersKey = "allocation:hot:v2:users:" + slotId;
        String requestKey = "allocation:request:v2:" + requestId;

        try {
            long now = System.currentTimeMillis();
            redis.opsForHash().putAll(snapshotKey, Map.of(
                    "stationId", String.valueOf(stationId),
                    "status", "OPEN",
                    "openAtMillis", String.valueOf(now - 1_000),
                    "endAtMillis", String.valueOf(now + 60_000)
            ));
            redis.opsForValue().set(stockKey, "2", Duration.ofMinutes(5));
            redis.expire(snapshotKey, Duration.ofMinutes(5));

            HotAllocationRedisService service = new HotAllocationRedisService(
                    redis,
                    mock(RedissonClient.class),
                    mock(AllocationMapper.class),
                    mock(DutySlotMapper.class),
                    true,
                    100,
                    1,
                    1
            );

            var accepted = service.accept(requestId, userId, officerId, stationId, slotId);
            assertThat(accepted.status()).isEqualTo("PRE_RESERVED");
            assertThat(redis.opsForValue().get(stockKey)).isEqualTo("1");

            service.accept(requestId, userId, officerId, stationId, slotId);
            assertThat(redis.opsForValue().get(stockKey)).isEqualTo("1");
            assertThatThrownBy(() -> service.accept(
                    UUID.randomUUID().toString(), userId, officerId, stationId, slotId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));

            AllocationResultEvent rejected = new AllocationResultEvent(
                    requestId, userId, officerId, stationId, slotId,
                    "REJECTED", null, null, "OUT_OF_STOCK", "时段余量不足");
            service.complete(rejected);
            service.complete(rejected);

            assertThat(redis.opsForValue().get(stockKey)).isEqualTo("2");
            assertThat(redis.opsForHash().hasKey(usersKey, String.valueOf(officerId))).isFalse();
            assertThat(service.getRequest(requestId).status()).isEqualTo("REJECTED");
            assertThat(redis.getExpire(requestKey)).isPositive();
        } finally {
            redis.delete(List.of(snapshotKey, stockKey, usersKey, requestKey));
        }
    }
}
