package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 勤务模块表。
 *
 * <p>对应 read.txt 中「后台管理系统中各个模块的增删改查及比重设置」。模块自带默认比重，
 * 比重参与效能计算：模块效能 = 配置人数 × 模块比重 × 模块内平均个体效能分。</p>
 */
@TableName(value = "duty_module")
@Data
public class DutyModule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模块编码，如 PATROL / HOUSEHOLD / TRAFFIC / CRIMINAL / COMMAND
     */
    @TableField(value = "module_code")
    private String moduleCode;

    /**
     * 模块名称，如 巡逻、户籍、交警、刑侦、指挥
     */
    @TableField(value = "module_name")
    private String moduleName;

    /**
     * 模块比重（百分比，0-100），后台可调整
     */
    @TableField(value = "ratio")
    private Integer ratio;

    /**
     * 是否属于体能类模块。体能类模块的个体效能分会被体检伤病结论下调。
     */
    @TableField(value = "physical_required")
    private Integer physicalRequired;

    /**
     * 状态：ENABLED / DISABLED
     */
    @TableField(value = "status")
    private String status;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
