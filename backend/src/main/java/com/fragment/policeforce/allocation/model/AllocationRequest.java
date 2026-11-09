package com.fragment.policeforce.allocation.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Durable result ledger for an asynchronous hot-allocation request.
 *
 * <p>Redis owns the short-lived acceptance state. This row is created by the
 * confirmation consumer and records only the authoritative MySQL outcome.</p>
 */
@Data
@TableName("allocation_request")
public class AllocationRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("request_id")
    private String requestId;

    @TableField("user_id")
    private Long userId;

    /**
     * 被调配的警员
     */
    @TableField("officer_id")
    private Long officerId;

    @TableField("station_id")
    private Long stationId;

    @TableField("slot_id")
    private Long slotId;

    @TableField("status")
    private String status;

    @TableField("allocation_id")
    private Long allocationId;

    @TableField("reject_code")
    private String rejectCode;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
