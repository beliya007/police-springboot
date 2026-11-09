package com.fragment.policeforce.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 警局配置人数设置请求。
 *
 * <p>对应 read.txt 中「配置人数的详情展示页面」的数据来源：
 * 某警局在各勤务模块上的核定配置人数。</p>
 *
 * <p>提交后立即触发该警局的<strong>增量重算</strong>（异步），
 * 让实际效能与缺编人数跟着更新。</p>
 */
@Data
public class StationHeadcountUpdateDTO {

    /**
     * 警局 ID
     */
    @NotNull(message = "警局 ID 不能为空")
    private Long stationId;

    /**
     * 各模块的配置人数
     */
    @NotEmpty(message = "配置人数不能为空")
    @Valid
    private List<Item> items;

    /**
     * 单个模块的配置人数。
     */
    @Data
    public static class Item {

        @NotNull(message = "模块 ID 不能为空")
        private Long moduleId;

        @Min(value = 0, message = "配置人数不能为负")
        private Integer configuredHeadcount;
    }
}
