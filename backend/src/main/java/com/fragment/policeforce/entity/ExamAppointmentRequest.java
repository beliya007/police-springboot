package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 体检预约请求账本。
 *
 * <p>这是消费端双重幂等的第一层（数据库唯一索引去重表）：{@code request_id} 唯一键，
 * 同一个客户端请求无论被 RocketMQ 投递多少次，账本只会有一条记录。</p>
 */
@TableName(value = "exam_appointment_request")
@Data
public class ExamAppointmentRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 客户端幂等键（UUID），全局唯一
     */
    @TableField(value = "request_id")
    private String requestId;

    @TableField(value = "officer_id")
    private Long officerId;

    @TableField(value = "slot_id")
    private Long slotId;

    @TableField(value = "batch_id")
    private Long batchId;

    /**
     * 状态：PROCESSING / CONFIRMED / REJECTED / EXPIRED
     */
    @TableField(value = "status")
    private String status;

    @TableField(value = "appointment_id")
    private Long appointmentId;

    /**
     * 候补记录 ID（进入候补队列时写入）
     */
    @TableField(value = "waiting_id")
    private Long waitingId;

    @TableField(value = "reject_code")
    private String rejectCode;

    @TableField(value = "reject_reason")
    private String rejectReason;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "completed_at")
    private LocalDateTime completedAt;
}
