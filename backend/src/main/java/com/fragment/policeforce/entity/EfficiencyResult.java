package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 效能计算结果快照（警局 + 模块粒度）。
 *
 * <p>快照按计算版本保留，看板展示最新版本，历史版本可用于对比「调整配置人数前后」
 * 「调整权重前后」的效能变化。</p>
 */
@TableName(value = "efficiency_result")
@Data
public class EfficiencyResult {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "station_id")
    private Long stationId;

    @TableField(value = "module_id")
    private Long moduleId;

    @TableField(value = "calc_version")
    private Long calcVersion;

    @TableField(value = "batch_no")
    private String batchNo;

    @TableField(value = "configured_headcount")
    private Integer configuredHeadcount;

    /**
     * 有效警力人数（体检合格且在编，FAIL 不计入）
     */
    @TableField(value = "effective_headcount")
    private Integer effectiveHeadcount;

    @TableField(value = "average_officer_score")
    private Double averageOfficerScore;

    /**
     * 模块效能分 = 配置人数 × 模块比重 × 平均个体效能分
     */
    @TableField(value = "module_score")
    private Double moduleScore;

    @TableField(value = "module_ratio")
    private Integer moduleRatio;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
