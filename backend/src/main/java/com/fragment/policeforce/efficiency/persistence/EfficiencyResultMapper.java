package com.fragment.policeforce.efficiency.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.EfficiencyResult;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 效能结果快照 Mapper。
 */
public interface EfficiencyResultMapper extends BaseMapper<EfficiencyResult> {

    /**
     * 查询某批次产出的全部结果（分片粒度）。
     */
    @Select("""
            SELECT * FROM efficiency_result
            WHERE batch_no = #{batchNo}
            """)
    List<EfficiencyResult> selectByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 查询某警局在指定版本下的全部模块结果。
     */
    @Select("""
            SELECT * FROM efficiency_result
            WHERE station_id = #{stationId}
              AND calc_version = #{calcVersion}
            """)
    List<EfficiencyResult> selectByStationAndVersion(@Param("stationId") Long stationId,
                                                     @Param("calcVersion") Long calcVersion);

    /**
     * 查询某警局的最新版本结果（看板读路径）。
     */
    @Select("""
            SELECT r.* FROM efficiency_result r
            INNER JOIN station_efficiency e ON e.station_id = r.station_id
            WHERE r.station_id = #{stationId}
              AND r.calc_version = e.latest_calc_version
            """)
    List<EfficiencyResult> selectLatestByStation(@Param("stationId") Long stationId);
}
