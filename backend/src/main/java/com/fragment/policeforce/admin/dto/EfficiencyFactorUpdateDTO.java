package com.fragment.policeforce.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 效能因素权重设置请求。
 *
 * <p>对应 read.txt 中「后台管理系统中效能因素（年龄，性别）比重的设置页面」。
 * 提交时必须整组提交某个维度下的所有取值，因为预检要求
 * 「同一维度内所有取值的权重之和等于 100」。</p>
 */
@Data
public class EfficiencyFactorUpdateDTO {

    /**
     * 因素维度：AGE / GENDER
     */
    @NotBlank(message = "因素维度不能为空")
    private String factorType;

    /**
     * 维度下的取值与权重
     */
    @NotNull(message = "因素配置不能为空")
    private java.util.List<Item> items;

    /**
     * 单个取值的权重配置。
     */
    @Data
    public static class Item {

        /**
         * 因素 ID。新增的取值时传 null。
         */
        private Long id;

        /**
         * 维度取值。AGE 维度为年龄段（如 25-35），GENDER 维度为 MALE / FEMALE
         */
        @NotBlank(message = "因素取值不能为空")
        @Size(max = 64, message = "因素取值不能超过 64 个字符")
        private String factorValue;

        @NotNull(message = "权重不能为空")
        @Min(value = 0, message = "权重不能小于 0")
        @Max(value = 100, message = "权重不能大于 100")
        private Integer weight;

        /**
         * 体检伤病时对该维度效能分的下调比例（百分比 0-100）
         */
        @Min(value = 0, message = "伤病下调比例不能小于 0")
        @Max(value = 100, message = "伤病下调比例不能大于 100")
        private Integer injuryPenalty;

        /**
         * 状态：ENABLED / DISABLED
         */
        private String status;
    }
}
