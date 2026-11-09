package com.fragment.policeforce.dto;

import lombok.Data;

@Data
public class DutySlotPageQueryDTO {

    private Long stationId;

    private String status;

    private String slotType;

    private Integer pageNum;

    private Integer pageSize;
}
