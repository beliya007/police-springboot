package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 体检候补记录表。
 *
 * <p>Redis ZSet 承担候补队列的实时排序与原子出队，本表是持久化快照，
 * 保证 Redis 不可用时候补请求不丢失；同时 {@code (slot_id, officer_id)} 唯一键
 * 是递补幂等的第一道防线。</p>
 */
@TableName(value = "exam_waiting")
@Data
public class ExamWaiting {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "slot_id")
    private Long slotId;

    @TableField(value = "batch_id")
    private Long batchId;

    @TableField(value = "officer_id")
    private Long officerId;

    /**
     * 候补入队序号（与 Redis ZSet score 一致，保证 FIFO 可重建）
     */
    @TableField(value = "sequence_no")
    private Long sequenceNo;

    @TableField(value = "request_id")
    private String requestId;

    /**
     * 状态：WAITING 排队中 / PROMOTED 已递补 / EXPIRED 已过期
     */
    @TableField(value = "status")
    private String status;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
