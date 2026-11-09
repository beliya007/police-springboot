package com.fragment.policeforce.allocation.api.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AllocationVO {

    private Long id;
    private Long stationId;
    private String allocationNo;
    private String status;
    private String sourceType;
    private String cancelReason;
    private LocalDateTime createdAt;

    private String stationName;
    private String stationCode;
    private String location;

    private LocalDateTime startDatetime;
    private LocalDateTime endDatetime;
    private LocalDateTime checkedInAt;
    private LocalDateTime autoCancelDeadline;
    private Boolean checkedIn;

    private String userNickname;
    private String userPhone;
}
