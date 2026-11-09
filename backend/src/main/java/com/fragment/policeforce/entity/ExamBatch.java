package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 体检批次表。
 *
 * <p>一个批次代表一次集中体检的放号事件。批次名额按各警局「配置人数 - 有效警力」的缺编比例分配，
 * 使体检资源优先流向警力缺口最大的单位。</p>
 */
@TableName(value = "exam_batch")
@Data
public class ExamBatch {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 批次号（全局唯一），用于查询与幂等
     */
    @TableField(value = "batch_no")
    private String batchNo;

    /**
     * 批次名称，如 2026 年第二季度集中体检
     */
    @TableField(value = "batch_name")
    private String batchName;

    /**
     * 体检机构
     */
    @TableField(value = "institution")
    private String institution;

    /**
     * 放号时间：早于该时间提交的预约请求直接拒绝
     */
    @TableField(value = "release_time")
    private LocalDateTime releaseTime;

    /**
     * 批次截止时间
     */
    @TableField(value = "close_time")
    private LocalDateTime closeTime;

    /**
     * 批次总名额
     */
    @TableField(value = "total_quota")
    private Integer totalQuota;

    /**
     * 状态：PREPARED 已准备 / RELEASED 已放号 / CLOSED 已结束
     */
    @TableField(value = "status")
    private String status;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
