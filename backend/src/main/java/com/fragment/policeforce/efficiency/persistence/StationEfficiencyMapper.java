package com.fragment.policeforce.efficiency.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.StationEfficiency;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 警局效能最新态 Mapper。
 *
 * <p>{@link #casUpdateLatest} 是本项目「结果覆盖的乐观 CAS 收敛」的核心：
 * 只有当传入的 calc_version 大于当前 latest_calc_version 时才允许覆盖。
 * 这样四个触发源（全局权重调整、模块比重调整、单警局人数调整、数据补录）
 * 并发重算同一警局时，旧结果不会覆盖新结果，杜绝「旧权重 + 新人数」的错误组合。</p>
 */
public interface StationEfficiencyMapper extends BaseMapper<StationEfficiency> {

    /**
     * 乐观 CAS 更新：仅当 calc_version 更大时覆盖。
     *
     * @return 受影响行数，0 表示本次结果被更新的版本抢先，本分片直接放弃写入
     */
    @Update("""
            UPDATE station_efficiency
            SET latest_calc_version = #{calcVersion},
                station_score = #{stationScore},
                configured_headcount = #{configuredHeadcount},
                effective_headcount = #{effectiveHeadcount},
                actual_effectiveness = #{actualEffectiveness},
                shortage_headcount = #{shortageHeadcount},
                calculated_at = NOW(),
                updated_at = NOW()
            WHERE station_id = #{stationId}
              AND latest_calc_version < #{calcVersion}
            """)
    int casUpdateLatest(@Param("stationId") Long stationId,
                        @Param("calcVersion") Long calcVersion,
                        @Param("stationScore") Double stationScore,
                        @Param("configuredHeadcount") Integer configuredHeadcount,
                        @Param("effectiveHeadcount") Integer effectiveHeadcount,
                        @Param("actualEffectiveness") Double actualEffectiveness,
                        @Param("shortageHeadcount") Integer shortageHeadcount);

    /**
     * 首次写入某个警局的效能最新态。
     *
     * <p>用 INSERT IGNORE 而不是「先查后插」，避免并发首算时两个分片都走插入分支。</p>
     */
    @Insert("""
            INSERT IGNORE INTO station_efficiency
                (station_id, latest_calc_version, station_score, configured_headcount,
                 effective_headcount, actual_effectiveness, shortage_headcount,
                 calculated_at, updated_at)
            VALUES
                (#{stationId}, 0, 0, 0, 0, 0, 0, NULL, NOW())
            """)
    int insertIfAbsent(@Param("stationId") Long stationId);

    /**
     * 读取当前生效版本，用于计算前判断是否需要跳过。
     */
    @org.apache.ibatis.annotations.Select("""
            SELECT latest_calc_version FROM station_efficiency WHERE station_id = #{stationId}
            """)
    Long selectLatestVersion(@Param("stationId") Long stationId);
}
