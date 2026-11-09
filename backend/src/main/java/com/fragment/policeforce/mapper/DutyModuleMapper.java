package com.fragment.policeforce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.DutyModule;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 勤务模块 Mapper。
 */
public interface DutyModuleMapper extends BaseMapper<DutyModule> {

    /**
     * 查询启用的模块（效能计算按模块比重展开）。
     */
    @Select("""
            SELECT * FROM duty_module
            WHERE status = 'ENABLED'
            ORDER BY id
            """)
    List<DutyModule> selectEnabled();

    /**
     * 按 ID 集合批量查询，供分片计算一次性取回模块比重。
     */
    @Select("""
            <script>
            SELECT * FROM duty_module WHERE id IN
            <foreach collection="moduleIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<DutyModule> selectByIds(@Param("moduleIds") List<Long> moduleIds);
}
