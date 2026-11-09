package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 警力调配名额时段表。
 *
 * <p>名额被两个不同单位同时抢走是业务上不可接受的风险，
 * 因此在名额之外还需要校验「同一名警员不能被重复派出」，
 * 这一点在 {@code allocation_assignment} 的唯一键上兜底。</p>
 */
@TableName(value ="duty_slot")
@Data
public class DutySlot {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属警局
     */
    @TableField(value = "station_id")
    private Long stationId;

    /**
     * 勤务模块（巡逻/户籍/交警/刑侦/指挥），决定这个名额用于哪个模块
     */
    @TableField(value = "module_id")
    private Long moduleId;

    /**
     * 开始时间
     */
    @TableField(value = "start_datetime")
    private LocalDateTime startDatetime;

    /**
     * 结束时间
     */
    @TableField(value = "end_datetime")
    private LocalDateTime endDatetime;

    /**
     * 时段类型 NORMAL/HOT
     */
    @TableField(value = "slot_type")
    private String slotType;

    /**
     * 开放预约时间，热门时段使用
     */
    @TableField(value = "open_time")
    private LocalDateTime openTime;

    /**
     * 总名额
     */
    @TableField(value = "total_quota")
    private Integer totalQuota;

    /**
     * 剩余名额
     */
    @TableField(value = "remain_quota")
    private Integer remainQuota;

    /**
     * 状态 OPEN/CLOSED
     */
    @TableField(value = "status")
    private String status;

    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}