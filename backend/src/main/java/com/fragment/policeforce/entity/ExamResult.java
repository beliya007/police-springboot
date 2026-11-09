package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 体检结论表。
 *
 * <p>体检结论是效能计算的数据来源：
 * {@code FAIL} 的警员不计入有效警力，{@code INJURED} 的警员按 injury_penalty 下调体能类模块的效能分。
 * 结论落库后触发受影响警局+模块的增量重算，而不是全量重算。</p>
 */
@TableName(value = "exam_result")
@Data
public class ExamResult {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "appointment_id")
    private Long appointmentId;

    @TableField(value = "officer_id")
    private Long officerId;

    /**
     * 所属警局（冗余，便于按警局增量重算）
     */
    @TableField(value = "station_id")
    private Long stationId;

    @TableField(value = "module_id")
    private Long moduleId;

    /**
     * 结论：PASS 合格 / FAIL 不合格 / INJURED 有伤病
     */
    @TableField(value = "conclusion")
    private String conclusion;

    @TableField(value = "conclusion_note")
    private String conclusionNote;

    @TableField(value = "examined_at")
    private LocalDateTime examinedAt;

    @TableField(value = "recorded_by")
    private String recordedBy;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
