package com.fragment.policeforce.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminDashboardVO {

    private Long totalAllocationCount;

    private Long todayAllocationCount;

    private Long activeAllocationCount;

    private Long finishedAllocationCount;

    private Long cancelledAllocationCount;

    private Long totalStationCount;

    private Long availableStationCount;

    private Long openSlotCount;

    private Long hotOpenSlotCount;

    private Long pendingReminderCount;

    private Long unreadNotificationCount;

    private Long pendingAuditOutboxCount;

    private List<AdminDashboardHotSlotVO> hotSlots = new ArrayList<>();

    private List<AdminDashboardStationHeatVO> topStations = new ArrayList<>();

}
