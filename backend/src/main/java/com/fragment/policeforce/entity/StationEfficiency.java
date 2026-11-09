package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 警局效能最新态。
 *
 * <p>{@code latest_calc_version} 是乐观 CAS 的锚点：全局权重调整、模块比重调整、
 * 单警局人数调整、数据补录四个触发源可能并发重算同一警局，
 * 只有更大的 calc_version 才允许覆盖本行，杜绝「旧权重 + 新人数」的错误组合。</p>
 */
@TableName(value = "station_efficiency")
@Data
public class StationEfficiency {

    @TableId(value = "station_id")
    private Long stationId;

    /**
     * 当前生效的计算版本
     */
    @TableField(value = "latest_calc_version")
    private Long latestCalcVersion;

    /**
     * 警局总效能分 = Σ 模块效能分
     */
    @TableField(value = "station_score")
    private Double stationScore;

    @TableField(value = "configured_headcount")
    private Integer configuredHeadcount;

    @TableField(value = "effective_headcount")
    private Integer effectiveHeadcount;

    /**
     * 实际效能 = 有效警力 / 配置人数
     */
    @TableField(value = "actual_effectiveness")
    private Double actualEffectiveness;

    /**
     * 缺编人数 = 配置人数 - 有效警力
     */
    @TableField(value = "shortage_headcount")
    private Integer shortageHeadcount;

    @TableField(value = "calculated_at")
    private LocalDateTime calculatedAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
