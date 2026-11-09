package com.fragment.policeforce.vo;

import lombok.Data;

@Data
public class AdminDashboardStationHeatVO {

    private Long stationId;

    private String stationName;

    private String stationCode;

    private String stationType;

    private Long allocationCount;

    private Long activeAllocationCount;
}
