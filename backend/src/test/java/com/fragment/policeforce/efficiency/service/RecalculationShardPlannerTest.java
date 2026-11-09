package com.fragment.policeforce.efficiency.service;

import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.mapper.PoliceStationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecalculationShardPlannerTest {

    @Mock PoliceStationMapper stationMapper;

    private RecalculationShardPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new RecalculationShardPlanner(stationMapper);
        ReflectionTestUtils.setField(planner, "shardSize", 20);
        ReflectionTestUtils.setField(planner, "maxShards", 64);
    }

    @Test
    void emptyStationListShouldProduceNoShard() {
        assertThat(planner.plan(List.of())).isEmpty();
    }

    @Test
    void stationsShouldBeSplitByShardSize() {
        List<Long> ids = LongStream.rangeClosed(1, 45).boxed().toList();

        var plans = planner.plan(ids);

        assertThat(plans).hasSize(3);
        assertThat(plans.get(0).stationIds()).hasSize(20);
        assertThat(plans.get(1).stationIds()).hasSize(20);
        assertThat(plans.get(2).stationIds()).hasSize(5);
        assertThat(plans.get(0).shardIndex()).isZero();
    }

    @Test
    void scanCountShouldCollapseWithShardSize() {
        List<Long> ids = LongStream.rangeClosed(1, 200).boxed().toList();

        var plans = planner.plan(ids);

        // 200 个警局 → 10 个分片 → 10 次表扫描，而不是 200 次
        assertThat(plans).hasSize(10);
        assertThat(plans.stream().mapToInt(plan -> plan.stationIds().size()).sum()).isEqualTo(200);
    }

    @Test
    void exceedingMaxShardsShouldMergeRemainderInsteadOfDropping() {
        ReflectionTestUtils.setField(planner, "shardSize", 10);
        ReflectionTestUtils.setField(planner, "maxShards", 3);
        List<Long> ids = LongStream.rangeClosed(1, 50).boxed().toList();

        var plans = planner.plan(ids);

        assertThat(plans).hasSize(3);
        // 不能丢警局：否则这些警局的效能会永远停留在旧版本
        assertThat(plans.stream().mapToInt(plan -> plan.stationIds().size()).sum()).isEqualTo(50);
    }

    @Test
    void stationScopeShouldResolveSingleStation() {
        when(stationMapper.selectById(7L)).thenReturn(station(7L));

        var plans = planner.plan(EfficiencyConstants.SCOPE_STATION, 7L, null);

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).stationIds()).containsExactly(7L);
    }

    @Test
    void missingStationShouldProduceNoShard() {
        when(stationMapper.selectById(7L)).thenReturn(null);

        assertThat(planner.plan(EfficiencyConstants.SCOPE_STATION, 7L, null)).isEmpty();
    }

    @Test
    void allScopeShouldUseFullStationTable() {
        when(stationMapper.selectAllForCalculation()).thenReturn(List.of(station(1L), station(2L)));

        var plans = planner.plan(EfficiencyConstants.SCOPE_ALL, null, null);

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).stationIds()).containsExactly(1L, 2L);
    }

    @Test
    void districtScopeShouldQueryStationsOfThatDistrict() {
        when(stationMapper.selectByDistrict("朝阳")).thenReturn(List.of(station(3L), station(4L)));

        var plans = planner.plan(EfficiencyConstants.SCOPE_DISTRICT, null, "朝阳");

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).stationIds()).containsExactly(3L, 4L);
    }

    private PoliceStation station(Long id) {
        PoliceStation station = new PoliceStation();
        station.setId(id);
        return station;
    }
}
