package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 效能因素权重表。
 *
 * <p>对应 read.txt 中「后台管理系统中效能因素（年龄，性别）比重的设置页面」。
 * 每条记录是「某个因素维度下某个取值」的权重，权重按维度分组求和必须等于 100，
 * 否则预检阶段直接拒绝 —— 这是异步重算链路的第一道参数校验。</p>
 */
@TableName(value = "efficiency_factor")
@Data
public class EfficiencyFactor {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 因素维度：AGE / GENDER
     */
    @TableField(value = "factor_type")
    private String factorType;

    /**
     * 维度取值。AGE 维度为年龄段（如 25-35），GENDER 维度为 MALE / FEMALE
     */
    @TableField(value = "factor_value")
    private String factorValue;

    /**
     * 权重（百分比，0-100）。同一维度内所有取值的权重和必须等于 100。
     */
    @TableField(value = "weight")
    private Integer weight;

    /**
     * 体检伤病时对该维度效能分的下调比例（百分比，0-100）。0 表示不因伤病下调。
     */
    @TableField(value = "injury_penalty")
    private Integer injuryPenalty;

    /**
     * 状态：ENABLED / DISABLED
     */
    @TableField(value = "status")
    private String status;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
