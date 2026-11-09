package com.fragment.policeforce.common.redis;

import com.fragment.policeforce.vo.PoliceStationVO;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceCatalogCacheTest {

    @Test
    void cachesOnlyTheHomeCatalogAndCanEvictIt() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            StationCatalogCache cacheService = context.getBean(StationCatalogCache.class);
            AtomicInteger loads = new AtomicInteger();

            List<PoliceStationVO> first = cacheService.getHomeStationList(() -> catalog(loads.incrementAndGet()));
            List<PoliceStationVO> second = cacheService.getHomeStationList(() -> catalog(loads.incrementAndGet()));

            assertThat(first.get(0).getStationName()).isEqualTo("资源版本-1");
            assertThat(second.get(0).getStationName()).isEqualTo("资源版本-1");
            assertThat(loads).hasValue(1);

            cacheService.invalidateHomeStationList();
            List<PoliceStationVO> afterEviction =
                    cacheService.getHomeStationList(() -> catalog(loads.incrementAndGet()));

            assertThat(afterEviction.get(0).getStationName()).isEqualTo("资源版本-2");
            assertThat(loads).hasValue(2);
        }
    }

    @Test
    void delayedEvictionRemovesAStaleValueWrittenDuringTheRaceWindow() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(DelayedTestConfig.class)) {
            StationCatalogCache cacheService = context.getBean(StationCatalogCache.class);
            AtomicInteger loads = new AtomicInteger();

            cacheService.getHomeStationList(() -> catalog(loads.incrementAndGet()));
            cacheService.invalidateHomeStationList();
            cacheService.getHomeStationList(() -> catalog(loads.incrementAndGet()));
            Thread.sleep(100);

            List<PoliceStationVO> afterSecondEviction =
                    cacheService.getHomeStationList(() -> catalog(loads.incrementAndGet()));

            assertThat(afterSecondEviction.get(0).getStationName()).isEqualTo("资源版本-3");
            assertThat(loads).hasValue(3);
        }
    }

    private List<PoliceStationVO> catalog(int version) {
        PoliceStationVO station = new PoliceStationVO();
        station.setId(1L);
        station.setStationName("资源版本-" + version);
        return List.of(station);
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(StationCatalogCache.RESOURCE_HOME_CACHE);
        }

        @Bean
        StationCatalogCache resourceCatalogCache(CacheManager cacheManager) {
            return new StationCatalogCache(cacheManager, 0);
        }
    }

    @Configuration
    @EnableCaching
    static class DelayedTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(StationCatalogCache.RESOURCE_HOME_CACHE);
        }

        @Bean
        StationCatalogCache resourceCatalogCache(CacheManager cacheManager) {
            return new StationCatalogCache(cacheManager, 30);
        }
    }
}
