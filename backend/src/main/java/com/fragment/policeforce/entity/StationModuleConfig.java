package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 警局勤务模块配置人数表。
 *
 * <p>对应 read.txt 中「警局配置人数的展示界面」与「配置人数的详情展示页面」。
 * 这是「配置人数」这一核心概念的数据落点：某警局在某勤务模块上被核定编制多少人。</p>
 */
@TableName(value = "station_module_config")
@Data
public class StationModuleConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "station_id")
    private Long stationId;

    @TableField(value = "module_id")
    private Long moduleId;

    /**
     * 核定配置人数
     */
    @TableField(value = "configured_headcount")
    private Integer configuredHeadcount;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
