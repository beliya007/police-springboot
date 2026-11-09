package com.fragment.policeforce.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class PoliceStationVO implements Serializable {
    private Long id;
    private String stationCode;
    private String stationName;
    private String stationType;
    private String stationTypeDesc;
    private String status;
    private String statusDesc;
    private String location;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
