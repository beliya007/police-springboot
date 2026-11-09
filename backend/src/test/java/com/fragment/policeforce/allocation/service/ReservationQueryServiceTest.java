package com.fragment.policeforce.allocation.service;

import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.entity.SysUser;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.service.PoliceStationService;
import com.fragment.policeforce.service.SysUserService;
import com.fragment.policeforce.allocation.api.vo.AllocationVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationQueryServiceTest {

    @Test
    void shouldUseAllocationSnapshotAndEnrichCurrentUserDetails() {
        AllocationMapper reservationMapper = mock(AllocationMapper.class);
        SysUserService userService = mock(SysUserService.class);
        PoliceStationService policeStationService = mock(PoliceStationService.class);
        AllocationQueryService queryService = new AllocationQueryService(
                reservationMapper, userService, policeStationService);

        LocalDateTime start = LocalDateTime.of(2026, 8, 15, 14, 0);
        AllocationAssignment allocation = new AllocationAssignment();
        allocation.setId(1L);
        allocation.setUserId(7L);
        allocation.setStationId(20L);
        allocation.setSlotId(10L);
        allocation.setStationName("预约时的巡防执勤点");
        allocation.setStationCode("PATROL-01");
        allocation.setStationLocation("城东分局 A 座");
        allocation.setSlotStartDatetime(start);
        allocation.setSlotEndDatetime(start.plusHours(2));

        SysUser user = new SysUser();
        user.setId(7L);
        user.setNickname("测试用户");
        user.setPhone("18800000000");
        when(reservationMapper.selectList(any())).thenReturn(List.of(allocation));
        when(userService.listByIds(anyCollection())).thenReturn(List.of(user));

        AllocationVO view = queryService.listByUserId(7L).get(0);

        assertThat(view.getStationName()).isEqualTo("预约时的巡防执勤点");
        assertThat(view.getLocation()).isEqualTo("城东分局 A 座");
        assertThat(view.getStartDatetime()).isEqualTo(start);
        assertThat(view.getEndDatetime()).isEqualTo(start.plusHours(2));
        assertThat(view.getUserNickname()).isEqualTo("测试用户");
        assertThat(view.getUserPhone()).isEqualTo("18800000000");
    }
}
