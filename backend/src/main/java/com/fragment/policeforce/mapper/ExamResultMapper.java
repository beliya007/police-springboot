package com.fragment.policeforce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.ExamResult;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 体检结论 Mapper。
 */
public interface ExamResultMapper extends BaseMapper<ExamResult> {

    /**
     * 按警员 ID 集合批量查询最新体检结论。
     *
     * <p>一个警员可能有多次体检记录，只有最新一次有效。
     * 用窗口函数取每个警员 id 最大的那一条，避免在内存里做全量分组。</p>
     */
    @Select("""
            <script>
            SELECT * FROM (
                SELECT r.*, ROW_NUMBER() OVER (PARTITION BY r.officer_id ORDER BY r.id DESC) AS rn
                FROM exam_result r
                WHERE r.officer_id IN
                <foreach collection="officerIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ) t WHERE t.rn = 1
            </script>
            """)
    List<ExamResult> selectLatestResults(@Param("officerIds") List<Long> officerIds);

    /**
     * 查询某警员某预约单的结论。
     */
    @Select("""
            SELECT * FROM exam_result WHERE appointment_id = #{appointmentId} LIMIT 1
            """)
    ExamResult selectByAppointmentId(@Param("appointmentId") Long appointmentId);
}
