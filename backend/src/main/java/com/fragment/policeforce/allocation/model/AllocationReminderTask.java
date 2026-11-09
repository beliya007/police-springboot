package com.fragment.policeforce.allocation.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("allocation_reminder_task")
@Data
public class AllocationReminderTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("allocation_id")
    private Long allocationId;

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

    @TableField("remind_type")
    private String remindType;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("plan_send_time")
    private LocalDateTime planSendTime;

    @TableField("status")
    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("last_error_message")
    private String lastErrorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("sent_at")
    private LocalDateTime sentAt;
}
