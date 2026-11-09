package com.fragment.policeforce.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量调整模块比重请求。
 *
 * <p>比重调整是「同步预检 + 202 异步重算」的典型场景：
 * 请求体只带要改的模块与新比重，服务端先同步校验，
 * 再异步触发全量重算，接口立即返回 202。</p>
 */
@Data
public class ModuleRatioUpdateDTO {

    /**
     * 模块 ID 与新比重的列表
     */
    @NotEmpty(message = "比重配置不能为空")
    @Valid
    private List<Item> items;

    /**
     * 单个模块的新比重。
     */
    @Data
    public static class Item {

        @NotNull(message = "模块 ID 不能为空")
        private Long moduleId;

        @Min(value = 0, message = "模块比重不能小于 0")
        @Max(value = 100, message = "模块比重不能大于 100")
        private Integer ratio;
    }
}
