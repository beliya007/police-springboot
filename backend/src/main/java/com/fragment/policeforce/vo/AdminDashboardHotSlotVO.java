package com.fragment.policeforce.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminDashboardHotSlotVO {

    private Long slotId;

    private Long stationId;

    private String stationName;

    private String stationCode;

    private LocalDateTime startDatetime;

    private LocalDateTime endDatetime;

    private Integer totalQuota;

    private Integer remainQuota;

    private Integer bookedQuota;

    private Integer occupancyRate;

    private String pressureLevel;
}
