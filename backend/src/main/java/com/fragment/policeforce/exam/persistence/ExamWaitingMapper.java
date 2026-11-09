package com.fragment.policeforce.exam.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.ExamWaiting;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 体检候补记录 Mapper。
 *
 * <p>候补队列的实时排序由 Redis ZSet 承担，本 Mapper 负责持久化快照与状态流转。
 * {@code uk_exam_waiting_slot_officer} 唯一键保证同一警员在同一时段只会有一条候补记录。</p>
 */
public interface ExamWaitingMapper extends BaseMapper<ExamWaiting> {

    /**
     * 把候补记录标记为已递补。
     *
     * <p>{@code AND status = 'WAITING'} 是递补幂等的第二道防线：
     * 只有仍在排队的记录才能被递补，重复递补会返回 0。</p>
     */
    @Update("""
            UPDATE exam_waiting
            SET status = 'PROMOTED',
                updated_at = NOW()
            WHERE id = #{waitingId}
              AND status = 'WAITING'
            """)
    int promoteIfWaiting(@Param("waitingId") Long waitingId);

    /**
     * 把候补记录标记为已过期。
     */
    @Update("""
            UPDATE exam_waiting
            SET status = 'EXPIRED',
                updated_at = NOW()
            WHERE id = #{waitingId}
              AND status = 'WAITING'
            """)
    int expireIfWaiting(@Param("waitingId") Long waitingId);
}
