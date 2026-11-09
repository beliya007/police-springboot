package com.fragment.policeforce.allocation.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName(value = "allocation_assignment")
@Data
public class AllocationAssignment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "allocation_no")
    private String allocationNo;

    @TableField(value = "user_id")
    private Long userId;

    /**
     * 被调配的警员（业务主体）。
     * 警员被两个单位同时派出是不可接受的风险，因此除「名额有限」之外，
     * 还有「同一名警员不能被重复派出」这第二重互斥，
     * 由 uk_allocation_officer_slot_active 唯一键在数据库层兜底。
     */
    @TableField(value = "officer_id")
    private Long officerId;

    @TableField(value = "station_id")
    private Long stationId;

    @TableField(value = "slot_id")
    private Long slotId;

    @TableField(value = "station_name")
    private String stationName;

    @TableField(value = "station_code")
    private String stationCode;

    @TableField(value = "station_location")
    private String stationLocation;

    @TableField(value = "slot_start_datetime")
    private LocalDateTime slotStartDatetime;

    @TableField(value = "slot_end_datetime")
    private LocalDateTime slotEndDatetime;

    @TableField(value = "checked_in_at")
    private LocalDateTime checkedInAt;

    @TableField(value = "auto_cancel_deadline")
    private LocalDateTime autoCancelDeadline;

    @TableField(value = "is_active")
    private Integer isActive;

    @TableField(value = "status")
    private String status;

    @TableField(value = "source_type")
    private String sourceType;

    @TableField(value = "cancel_reason")
    private String cancelReason;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
