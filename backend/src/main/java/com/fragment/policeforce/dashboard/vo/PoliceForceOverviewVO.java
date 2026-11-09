package com.fragment.policeforce.dashboard.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 警力规划总览（看板顶部汇总）。
 */
@Data
public class PoliceForceOverviewVO implements Serializable {

    /**
     * 警局总数
     */
    private Integer stationCount;

    /**
     * 全局核定配置人数合计
     */
    private Integer totalConfiguredHeadcount;

    /**
     * 全局有效警力合计
     */
    private Integer totalEffectiveHeadcount;

    /**
     * 全局实际效能
     */
    private Double overallEffectiveness;

    /**
     * 全局缺编人数
     */
    private Integer totalShortageHeadcount;

    /**
     * 警力缺口最大的警局（缺编人数 Top N）
     */
    private List<StationOverviewVO> shortageRanking;
}
