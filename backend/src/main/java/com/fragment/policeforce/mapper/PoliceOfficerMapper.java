package com.fragment.policeforce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.PoliceOfficer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 警员 Mapper。
 */
public interface PoliceOfficerMapper extends BaseMapper<PoliceOfficer> {

    /**
     * 按警局 ID 集合批量查询在编警员。
     *
     * <p>分片计算的关键读路径：一次查询取回整个分片的警员数据，
     * 在内存里按 (stationId, moduleId) 分组，避免逐个警局查询造成读放大。</p>
     */
    @Select("""
            <script>
            SELECT * FROM police_officer
            WHERE status = 'ACTIVE'
              AND station_id IN
            <foreach collection="stationIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<PoliceOfficer> selectActiveByStationIds(@Param("stationIds") List<Long> stationIds);

    /**
     * 查询某警员最新的体检结论（用于有效警力口径）。
     */
    @Select("""
            SELECT * FROM exam_result
            WHERE officer_id = #{officerId}
            ORDER BY id DESC
            LIMIT 1
            """)
    com.fragment.policeforce.entity.ExamResult selectLatestResult(@Param("officerId") Long officerId);

    /**
     * 按登录账号查警员。
     *
     * <p>体检预约的接口以 JWT 主体（登录账号）为准，
     * 通过它把「当前登录的人」映射成「可预约体检的警员」。</p>
     */
    @Select("""
            SELECT * FROM police_officer
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
            LIMIT 1
            """)
    PoliceOfficer selectByUserId(@Param("userId") Long userId);
}
