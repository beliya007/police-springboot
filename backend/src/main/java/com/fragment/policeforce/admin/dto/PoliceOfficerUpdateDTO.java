package com.fragment.policeforce.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 警员更新请求。
 */
@Data
public class PoliceOfficerUpdateDTO {

    @NotNull(message = "警员 ID 不能为空")
    private Long id;

    @NotBlank(message = "警员编号不能为空")
    @Size(max = 64, message = "警员编号不能超过 64 个字符")
    private String officerCode;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名不能超过 64 个字符")
    private String name;

    private Long userId;

    @NotNull(message = "所属警局不能为空")
    private Long stationId;

    @NotNull(message = "所属勤务模块不能为空")
    private Long moduleId;

    @NotBlank(message = "性别不能为空")
    private String gender;

    @NotNull(message = "出生日期不能为空")
    @Past(message = "出生日期不能晚于今天")
    private LocalDateTime birthDate;

    private String rankCode;

    private String status;
}
