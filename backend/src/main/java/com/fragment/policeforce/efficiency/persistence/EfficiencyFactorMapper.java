package com.fragment.policeforce.efficiency.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.EfficiencyFactor;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 效能因素权重 Mapper。
 */
public interface EfficiencyFactorMapper extends BaseMapper<EfficiencyFactor> {

    /**
     * 查询启用的因素权重（效能计算的唯一输入源）。
     */
    @Select("""
            SELECT * FROM efficiency_factor
            WHERE status = 'ENABLED'
            ORDER BY factor_type, id
            """)
    List<EfficiencyFactor> selectEnabled();

    /**
     * 查询某个维度下的因素权重。
     */
    @Select("""
            SELECT * FROM efficiency_factor
            WHERE factor_type = #{factorType}
              AND status = 'ENABLED'
            ORDER BY id
            """)
    List<EfficiencyFactor> selectEnabledByType(@Param("factorType") String factorType);
}
