package com.fragment.policeforce.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 勤务模块更新请求。
 */
@Data
public class DutyModuleUpdateDTO {

    @NotNull(message = "模块 ID 不能为空")
    private Long id;

    @NotBlank(message = "模块编码不能为空")
    @Size(max = 50, message = "模块编码不能超过 50 个字符")
    private String moduleCode;

    @NotBlank(message = "模块名称不能为空")
    @Size(max = 100, message = "模块名称不能超过 100 个字符")
    private String moduleName;

    /**
     * 模块比重（百分比 0-100）
     */
    @Min(value = 0, message = "模块比重不能小于 0")
    @Max(value = 100, message = "模块比重不能大于 100")
    private Integer ratio;

    /**
     * 是否属于体能类模块
     */
    private Integer physicalRequired;

    private String status;
}
