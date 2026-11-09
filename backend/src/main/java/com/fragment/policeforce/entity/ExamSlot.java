package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 体检时段表。
 *
 * <p>名额竞争的最小单位。500 并发抢 70 个名额的场景下，热点是同一个 slot 行，
 * Redis Lua 前置裁决把失败请求挡在 MySQL 之外，只让赢家进入落库阶段。</p>
 */
@TableName(value = "exam_slot")
@Data
public class ExamSlot {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "batch_id")
    private Long batchId;

    /**
     * 时段类型 NORMAL/HOT，HOT 表示放号瞬间的热门时段
     */
    @TableField(value = "slot_type")
    private String slotType;

    @TableField(value = "start_datetime")
    private LocalDateTime startDatetime;

    @TableField(value = "end_datetime")
    private LocalDateTime endDatetime;

    @TableField(value = "open_time")
    private LocalDateTime openTime;

    @TableField(value = "total_quota")
    private Integer totalQuota;

    @TableField(value = "remain_quota")
    private Integer remainQuota;

    @TableField(value = "status")
    private String status;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
