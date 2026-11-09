package com.fragment.policeforce.dashboard.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 模块占比与人数分布明细。
 *
 * <p>对应 read.txt 中「配置人数的详情展示页面（各模块占比及人数分布）」。</p>
 */
@Data
public class ModuleBreakdownVO implements Serializable {

    private Long moduleId;

    private String moduleCode;

    private String moduleName;

    /**
     * 模块比重（百分比）
     */
    private Integer ratio;

    /**
     * 是否属于体能类模块
     */
    private Boolean physicalRequired;

    /**
     * 该模块的核定配置人数
     */
    private Integer configuredHeadcount;

    /**
     * 该模块的有效警力人数
     */
    private Integer effectiveHeadcount;

    /**
     * 该模块的缺编人数
     */
    private Integer shortageHeadcount;

    /**
     * 模块内平均个体效能分
     */
    private Double averageOfficerScore;

    /**
     * 模块效能分 = 配置人数 × 比重 × 平均个体效能分
     */
    private Double moduleScore;

    /**
     * 该模块的配置人数占本警局配置人数的比例（百分比）
     */
    private Double headcountShare;

    /**
     * 该模块的有效警力占本警局有效警力的比例（百分比）
     */
    private Double effectiveShare;
}
