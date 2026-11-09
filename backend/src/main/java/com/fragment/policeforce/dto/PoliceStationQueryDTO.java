package com.fragment.policeforce.dto;


import lombok.Data;

@Data
public class PoliceStationQueryDTO {
    private String name;   // 模糊搜索：设备名称
    private String type;   // 精确筛选：设备类型 (TARGET_CAR/TEST_FIELD)
    private String status; // 精确筛选：设备状态 (AVAILABLE/MAINTAINING)
}
