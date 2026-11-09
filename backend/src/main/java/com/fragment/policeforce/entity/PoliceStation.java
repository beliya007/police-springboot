package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 警局表。
 *
 * <p>警局是配置人数、效能计算与警力调配三个域的聚合根。</p>
 */
@TableName(value = "police_station")
@Data
public class PoliceStation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 警局编号，如 PS-01
     */
    @TableField(value = "station_code")
    private String stationCode;

    /**
     * 警局名称，如 东城分局
     */
    @TableField(value = "station_name")
    private String stationName;

    /**
     * 所属区域（上级辖区），用于按区域批量重算
     */
    @TableField(value = "district")
    private String district;

    /**
     * 警局类型：DIVISION 分局 / SUB_STATION 派出所 / PRECINCT 警区
     */
    @TableField(value = "station_type")
    private String stationType;

    /**
     * 状态：AVAILABLE / MAINTAINING / DISABLED
     */
    @TableField(value = "status")
    private String status;

    /**
     * 所在位置
     */
    @TableField(value = "location")
    private String location;

    /**
     * 补充描述
     */
    @TableField(value = "description")
    private String description;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
