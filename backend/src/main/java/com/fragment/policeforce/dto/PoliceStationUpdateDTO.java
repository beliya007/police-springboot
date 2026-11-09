package com.fragment.policeforce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PoliceStationUpdateDTO {

    @NotNull(message = "资源ID不能为空")
    private Long id;

    @NotBlank(message = "资源编号不能为空")
    private String stationCode;

    @NotBlank(message = "资源名称不能为空")
    private String stationName;

    @NotBlank(message = "资源类型不能为空")
    private String stationType;

    @NotBlank(message = "资源状态不能为空")
    private String status;

    private String location;

    private String description;
}
