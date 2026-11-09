package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 警员表。
 *
 * <p>对应 read.txt 中「后台管理系统中各警局的人员信息的管理」。
 * 年龄（由 birth_date 推导）与性别是效能计算的两类效能因素；
 * 体检结论单独存在 exam_result 表，不冗余到本表。</p>
 */
@TableName(value = "police_officer")
@Data
public class PoliceOfficer {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 警员编号
     */
    @TableField(value = "officer_code")
    private String officerCode;

    /**
     * 姓名
     */
    @TableField(value = "name")
    private String name;

    /**
     * 关联的登录账号。体检预约的「本人操作」通过它把 JWT 主体映射到警员。
     */
    @TableField(value = "user_id")
    private Long userId;

    @TableField(value = "station_id")
    private Long stationId;

    @TableField(value = "module_id")
    private Long moduleId;

    /**
     * 性别：MALE / FEMALE
     */
    @TableField(value = "gender")
    private String gender;

    /**
     * 出生日期，效能计算按此推导年龄段
     */
    @TableField(value = "birth_date")
    private LocalDateTime birthDate;

    /**
     * 职级
     */
    @TableField(value = "rank_code")
    private String rankCode;

    /**
     * 状态：在编 ACTIVE / 调出 TRANSFERRED / 退休 RETIRED
     */
    @TableField(value = "status")
    private String status;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
