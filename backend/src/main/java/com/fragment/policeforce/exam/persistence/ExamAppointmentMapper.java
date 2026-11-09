package com.fragment.policeforce.exam.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.ExamAppointment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 体检预约单 Mapper。
 *
 * <p>「一人一档」由 {@code uk_exam_appointment_officer_slot_active} 唯一键保证：
 * 同一警员在同一时段只能持有一份有效预约。</p>
 */
public interface ExamAppointmentMapper extends BaseMapper<ExamAppointment> {

    /**
     * 查询某警员在某时段的活跃预约。
     */
    @Select("""
            SELECT * FROM exam_appointment
            WHERE officer_id = #{officerId}
              AND slot_id = #{slotId}
              AND is_active = 1
            LIMIT 1
            """)
    ExamAppointment selectActiveByOfficerAndSlot(@Param("officerId") Long officerId,
                                                  @Param("slotId") Long slotId);

    /**
     * 查询某时段的所有活跃预约（预热 Redis 库存时回填已占名额）。
     */
    @Select("""
            SELECT * FROM exam_appointment
            WHERE slot_id = #{slotId}
              AND is_active = 1
            """)
    List<ExamAppointment> selectActiveBySlot(@Param("slotId") Long slotId);

    /**
     * 查询某警员的所有活跃预约（一人一档的全局校验）。
     */
    @Select("""
            SELECT * FROM exam_appointment
            WHERE officer_id = #{officerId}
              AND is_active = 1
            """)
    List<ExamAppointment> selectActiveByOfficer(@Param("officerId") Long officerId);

    /**
     * 条件取消：只有仍是 BOOKED 且未签到时才允许取消。
     *
     * <p>{@code is_active} 置 NULL 而不是 0，是为了让 MySQL 唯一索引不再参与去重
     * （MySQL 认为多个 NULL 互不相同），从而允许该警员重新预约同一时段。</p>
     */
    @Update("""
            UPDATE exam_appointment
            SET status = 'CANCELLED',
                is_active = NULL,
                cancel_reason = #{reason},
                updated_at = NOW()
            WHERE id = #{appointmentId}
              AND status = 'BOOKED'
              AND is_active = 1
              AND checked_in_at IS NULL
            """)
    int cancelIfBooked(@Param("appointmentId") Long appointmentId,
                       @Param("reason") String reason);

    /**
     * 取消后把预约单标记为已完成（体检结束）。
     */
    @Update("""
            UPDATE exam_appointment
            SET status = 'COMPLETED',
                updated_at = NOW()
            WHERE id = #{appointmentId}
              AND status = 'BOOKED'
              AND is_active = 1
            """)
    int completeIfBooked(@Param("appointmentId") Long appointmentId);
}
