package com.fragment.policeforce.dashboard.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 警局配置人数与效能概览（看板卡片）。
 *
 * <p>对应 read.txt 中「警局配置人数的展示界面」与「警局实际效能的展示」。</p>
 */
@Data
public class StationOverviewVO implements Serializable {

    private Long stationId;

    private String stationCode;

    private String stationName;

    private String district;

    private String stationType;

    /**
     * 核定配置人数合计
     */
    private Integer configuredHeadcount;

    /**
     * 有效警力人数（体检合格且在编）
     */
    private Integer effectiveHeadcount;

    /**
     * 警局总效能分
     */
    private Double stationScore;

    /**
     * 实际效能 = 有效警力 / 配置人数
     */
    private Double actualEffectiveness;

    /**
     * 缺编人数 = 配置人数 - 有效警力
     */
    private Integer shortageHeadcount;

    /**
     * 当前生效的计算版本
     */
    private Long latestCalcVersion;

    /**
     * 各勤务模块的占比与人数分布
     */
    private List<ModuleBreakdownVO> modules;
}
