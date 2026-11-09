package com.fragment.policeforce.dto;

import lombok.Data;

@Data
public class PoliceStationPageQueryDTO {

    private String keyword;
    private String stationType;
    private String status;

    private Integer pageNum;
    private Integer pageSize;

}
