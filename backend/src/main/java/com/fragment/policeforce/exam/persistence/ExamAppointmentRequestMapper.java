package com.fragment.policeforce.exam.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.ExamAppointmentRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 体检预约请求账本 Mapper。
 *
 * <p>{@code insertProcessingIgnore} 是消费端双重幂等的数据库层保障：
 * 依赖 {@code uk_exam_appointment_request_id} 唯一键，
 * 同一个 request_id 无论被投递多少次，只会插入一条记录，
 * 第二次返回 0 表示「这是重复投递，直接 ACK」。</p>
 */
public interface ExamAppointmentRequestMapper extends BaseMapper<ExamAppointmentRequest> {

    /**
     * 尝试插入一条 PROCESSING 账本；重复 request_id 时返回 0 且不抛异常。
     */
    @Insert("""
            INSERT IGNORE INTO exam_appointment_request
                (request_id, officer_id, slot_id, batch_id, status, created_at)
            VALUES
                (#{requestId}, #{officerId}, #{slotId}, #{batchId}, 'PROCESSING', NOW())
            """)
    int insertProcessingIgnore(@Param("requestId") String requestId,
                                @Param("officerId") Long officerId,
                                @Param("slotId") Long slotId,
                                @Param("batchId") Long batchId);
}
