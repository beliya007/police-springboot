package com.fragment.policeforce.allocation.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AllocationPageQueryDTO {
    private Long userId;
    private Long stationId;
    private String status;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    private Integer pageNum;
    private Integer pageSize;
}
