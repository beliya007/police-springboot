package com.fragment.policeforce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.StationModuleConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 警局模块配置人数 Mapper。
 */
public interface StationModuleConfigMapper extends BaseMapper<StationModuleConfig> {

    /**
     * 按警局 ID 集合批量查询配置人数。
     *
     * <p>这是分片计算的关键读路径：一个分片只调用一次，
     * 在内存里按 (stationId, moduleId) 建索引，避免 N+1 查询。</p>
     */
    @Select("""
            <script>
            SELECT * FROM station_module_config WHERE station_id IN
            <foreach collection="stationIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<StationModuleConfig> selectByStationIds(@Param("stationIds") List<Long> stationIds);
}
