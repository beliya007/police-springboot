package com.fragment.policeforce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.PoliceStation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 警局 Mapper。
 */
public interface PoliceStationMapper extends BaseMapper<PoliceStation> {

    /**
     * 查询参与效能计算的全部警局（排除已停用）。
     *
     * <p>这是分片计算的读路径之一：分片规划阶段只查一次警局列表，
     * 用来切分分片，不做重复的全表扫描。</p>
     */
    @Select("""
            SELECT * FROM police_station
            WHERE status <> 'DISABLED'
            ORDER BY id
            """)
    List<PoliceStation> selectAllForCalculation();

    /**
     * 按区域查询警局。
     */
    @Select("""
            SELECT * FROM police_station
            WHERE district = #{district}
              AND status <> 'DISABLED'
            ORDER BY id
            """)
    List<PoliceStation> selectByDistrict(@Param("district") String district);

    /**
     * 查询某警局所属的区域（用于按区域重算时定位）。
     */
    @Select("""
            SELECT district FROM police_station WHERE id = #{stationId}
            """)
    String selectDistrictById(@Param("stationId") Long stationId);
}
