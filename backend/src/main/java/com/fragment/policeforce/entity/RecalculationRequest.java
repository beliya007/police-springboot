package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 效能重算请求账本。
 *
 * <p>消费端双重幂等的第一层：{@code request_id} 唯一键。
 * 一次重算请求可能覆盖多个警局，因此账本与批次是多对一关系。</p>
 */
@TableName(value = "recalculation_request")
@Data
public class RecalculationRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 客户端幂等键（UUID），全局唯一
     */
    @TableField(value = "request_id")
    private String requestId;

    /**
     * 批次号，关联 recalculation_shard
     */
    @TableField(value = "batch_no")
    private String batchNo;

    /**
     * 触发来源：FACTOR_WEIGHT 权重调整 / MODULE_RATIO 模块比重 / HEADCOUNT 配置人数
     * / EXAM_RESULT 体检结论 / MANUAL 手工触发
     */
    @TableField(value = "trigger_source")
    private String triggerSource;

    /**
     * 触发范围：ALL 全量 / DISTRICT 某区域 / STATION 某警局 / MODULE 某模块
     */
    @TableField(value = "scope_type")
    private String scopeType;

    /**
     * 触发范围的目标 ID（区域、警局或模块），全量时为空
     */
    @TableField(value = "scope_id")
    private Long scopeId;

    /**
     * 本次计算使用的计算版本（单调递增，用于乐观 CAS 收敛）
     */
    @TableField(value = "calc_version")
    private Long calcVersion;

    /**
     * 状态：PROCESSING / CONFIRMED / REJECTED / EXPIRED
     */
    @TableField(value = "status")
    private String status;

    @TableField(value = "affected_station_count")
    private Integer affectedStationCount;

    @TableField(value = "reject_code")
    private String rejectCode;

    @TableField(value = "reject_reason")
    private String rejectReason;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "completed_at")
    private LocalDateTime completedAt;
}
