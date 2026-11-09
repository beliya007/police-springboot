package com.fragment.policeforce.exam.service;

import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.StationEfficiency;
import com.fragment.policeforce.efficiency.persistence.StationEfficiencyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamQuotaAllocatorTest {

    @Mock StationEfficiencyMapper stationEfficiencyMapper;

    private ExamQuotaAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new ExamQuotaAllocator(stationEfficiencyMapper);
    }

    @Test
    void nonPositiveQuotaShouldReject() {
        assertThatThrownBy(() -> allocator.allocate(0))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
    }

    @Test
    void fullyStaffedStationsShouldReceiveNoQuota() {
        when(stationEfficiencyMapper.selectList(null)).thenReturn(List.of(
                station(1L, 0), station(2L, -5)));

        var allocation = allocator.allocate(70);

        assertThat(allocation.quotas()).isEmpty();
        assertThat(allocation.totalQuota()).isEqualTo(70);
    }

    @Test
    void quotaShouldFollowShortageRatio() {
        when(stationEfficiencyMapper.selectList(null)).thenReturn(List.of(
                station(1L, 30), station(2L, 10)));

        var allocation = allocator.allocate(40);

        // 缺编比例 30:10 → 分配 30 : 10（合计 40）
        assertThat(allocation.quotas()).containsEntry(1L, 30);
        assertThat(allocation.quotas()).containsEntry(2L, 10);
        assertThat(allocation.quotas().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(40);
    }

    @Test
    void quotaShouldBeFullyDistributedEvenWhenQuotaExceedsTotalShortage() {
        when(stationEfficiencyMapper.selectList(null)).thenReturn(List.of(
                station(1L, 30), station(2L, 10)));

        var allocation = allocator.allocate(70);

        // 名额多于总缺编时不允许浪费：剩余名额按缺编从大到小继续分配
        assertThat(allocation.quotas().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(70);
        assertThat(allocation.quotas().get(1L)).isGreaterThan(allocation.quotas().get(2L));
    }

    @Test
    void allocatedQuotaShouldNeverExceedShortageBeforeOverflow() {
        when(stationEfficiencyMapper.selectList(null)).thenReturn(List.of(
                station(1L, 3), station(2L, 100)));

        var allocation = allocator.allocate(70);

        // 缺编只有 3 的警局最多先拿 3 个
        assertThat(allocation.quotas().get(1L)).isLessThanOrEqualTo(3);
        assertThat(allocation.quotas().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(70);
    }

    @Test
    void distributedSumShouldAlwaysEqualTotalQuota() {
        when(stationEfficiencyMapper.selectList(null)).thenReturn(List.of(
                station(1L, 7), station(2L, 11), station(3L, 5)));

        var allocation = allocator.allocate(70);

        assertThat(allocation.quotas().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(70);
    }

    @Test
    void stationWithLargerShortageShouldGetMoreQuota() {
        when(stationEfficiencyMapper.selectList(null)).thenReturn(List.of(
                station(1L, 80), station(2L, 20)));

        var allocation = allocator.allocate(70);

        assertThat(allocation.quotas().get(1L)).isGreaterThan(allocation.quotas().get(2L));
    }

    private StationEfficiency station(Long stationId, int shortage) {
        StationEfficiency efficiency = new StationEfficiency();
        efficiency.setStationId(stationId);
        efficiency.setShortageHeadcount(shortage);
        return efficiency;
    }
}
