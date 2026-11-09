package com.fragment.policeforce.dashboard.persistence;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 看板聚合查询 Mapper。
 *
 * <p>看板要同时展示「配置人数」「实际效能」「模块占比及人数分布」，
 * 这些数据分散在四张表里。这里用几个「一次性聚合」的 SQL 把它们查出来，
 * 而不是让 Service 层做 N+1 查询：</p>
 * <ul>
 *   <li>每个警局一条聚合行（配置人数合计、有效警力合计、效能、缺编）；</li>
 *   <li>每个警局-模块一条明细行（配置人数、有效警力、比重、模块效能分）。</li>
 * </ul>
 *
 * <p>注意：明细行只取<b>当前生效版本</b>（{@code latest_calc_version}）的结果，
 * 避免把历史版本的快照混进当前看板。</p>
 */
public interface DashboardAggregateMapper {

    /**
     * 警局级聚合行。
     *
     * @param stationIds 需要统计的警局 ID；为空表示全部
     */
    @Select("""
            <script>
            SELECT s.id AS station_id,
                   s.station_code,
                   s.station_name,
                   s.district,
                   s.station_type,
                   s.status,
                   COALESCE(e.configured_headcount, 0) AS configured_headcount,
                   COALESCE(e.effective_headcount, 0) AS effective_headcount,
                   COALESCE(e.station_score, 0) AS station_score,
                   COALESCE(e.actual_effectiveness, 0) AS actual_effectiveness,
                   COALESCE(e.shortage_headcount, 0) AS shortage_headcount,
                   e.latest_calc_version
            FROM police_station s
            LEFT JOIN station_efficiency e ON e.station_id = s.id
            <where>
                <if test="stationIds != null and stationIds.size() > 0">
                    s.id IN
                    <foreach collection="stationIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                </if>
                s.status &lt;&gt; 'DISABLED'
            </where>
            ORDER BY s.station_code
            </script>
            """)
    List<StationAggregateRow> selectStationAggregates(@Param("stationIds") List<Long> stationIds);

    /**
     * 警局-模块级明细行（只取当前生效版本）。
     */
    @Select("""
            <script>
            SELECT r.station_id,
                   r.module_id,
                   m.module_code,
                   m.module_name,
                   m.ratio,
                   m.physical_required,
                   r.configured_headcount,
                   r.effective_headcount,
                   r.average_officer_score,
                   r.module_score
            FROM efficiency_result r
            INNER JOIN station_efficiency e ON e.station_id = r.station_id
                                            AND e.latest_calc_version = r.calc_version
            INNER JOIN duty_module m ON m.id = r.module_id
            <where>
                <if test="stationIds != null and stationIds.size() > 0">
                    r.station_id IN
                    <foreach collection="stationIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                </if>
            </where>
            ORDER BY r.station_id, r.module_id
            </script>
            """)
    List<ModuleBreakdownRow> selectModuleBreakdowns(@Param("stationIds") List<Long> stationIds);

    /**
     * 警局级聚合行。
     */
    class StationAggregateRow {
        public Long stationId;
        public String stationCode;
        public String stationName;
        public String district;
        public String stationType;
        public String status;
        public Integer configuredHeadcount;
        public Integer effectiveHeadcount;
        public Double stationScore;
        public Double actualEffectiveness;
        public Integer shortageHeadcount;
        public Long latestCalcVersion;
    }

    /**
     * 警局-模块级明细行。
     */
    class ModuleBreakdownRow {
        public Long stationId;
        public Long moduleId;
        public String moduleCode;
        public String moduleName;
        public Integer ratio;
        public Integer physicalRequired;
        public Integer configuredHeadcount;
        public Integer effectiveHeadcount;
        public Double averageOfficerScore;
        public Double moduleScore;
    }
}
