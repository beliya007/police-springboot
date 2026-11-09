package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 体检预约单。
 *
 * <p>「一人一档」由唯一键 {@code (officer_id, slot_id, is_active)} 保证：
 * 同一名警员在同一时段只能持有一份有效预约；取消后 is_active 置 NULL，
 * MySQL 唯一索引中 NULL 不参与去重，从而允许重新预约。</p>
 */
@TableName(value = "exam_appointment")
@Data
public class ExamAppointment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "appointment_no")
    private String appointmentNo;

    @TableField(value = "officer_id")
    private Long officerId;

    @TableField(value = "slot_id")
    private Long slotId;

    @TableField(value = "batch_id")
    private Long batchId;

    /**
     * 警员姓名冗余，便于列表展示
     */
    @TableField(value = "officer_name")
    private String officerName;

    @TableField(value = "slot_start_datetime")
    private LocalDateTime slotStartDatetime;

    @TableField(value = "slot_end_datetime")
    private LocalDateTime slotEndDatetime;

    /**
     * 是否为有效预约：1 有效 / NULL 已取消
     */
    @TableField(value = "is_active")
    private Integer isActive;

    /**
     * 状态：BOOKED 已预约 / CANCELLED 已取消 / CHECKED_IN 已签到 / COMPLETED 已完成
     */
    @TableField(value = "status")
    private String status;

    @TableField(value = "source_type")
    private String sourceType;

    /**
     * 是否由候补递补产生
     */
    @TableField(value = "from_waiting")
    private Integer fromWaiting;

    @TableField(value = "cancel_reason")
    private String cancelReason;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
