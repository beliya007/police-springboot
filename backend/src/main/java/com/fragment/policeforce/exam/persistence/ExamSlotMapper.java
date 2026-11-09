package com.fragment.policeforce.exam.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.ExamSlot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 体检时段 Mapper。
 *
 * <p>名额扣减用条件更新而非「先查后改」：{@code remain_quota >= #{need}} 是整个防超卖链路的
 * MySQL 侧兜底。500 并发抢 70 个名额时，所有请求争用同一行，
 * InnoDB 行锁会把并发更新串行化，受影响行数为 0 的请求即判定名额已满。</p>
 */
public interface ExamSlotMapper extends BaseMapper<ExamSlot> {

    /**
     * 条件扣减名额：名额不足时返回 0，调用方据此拒绝。
     */
    @Update("""
            UPDATE exam_slot
            SET remain_quota = remain_quota - #{need}
            WHERE id = #{slotId}
              AND status = 'OPEN'
              AND (open_time IS NULL OR open_time <= NOW())
              AND end_datetime > NOW()
              AND remain_quota >= #{need}
            """)
    int deductQuotaIfAvailable(@Param("slotId") Long slotId, @Param("need") int need);

    /**
     * 回补名额：取消预约或候补递补失败时把名额还回去。
     */
    @Update("""
            UPDATE exam_slot
            SET remain_quota = remain_quota + #{need}
            WHERE id = #{slotId}
              AND remain_quota + #{need} <= total_quota
            """)
    int restoreQuota(@Param("slotId") Long slotId, @Param("need") int need);
}
