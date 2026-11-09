package com.fragment.policeforce.efficiency.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.RecalculationShard;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 效能重算分片 Mapper。
 *
 * <p>分片状态机：PENDING → PROCESSING → DONE / FAILED。
 * 状态流转都带前置条件，保证同一分片不会被两个消费者同时执行。</p>
 */
public interface RecalculationShardMapper extends BaseMapper<RecalculationShard> {

    /**
     * 抢占分片：只有 PENDING 的分片能被抢占。
     */
    @Update("""
            UPDATE recalculation_shard
            SET status = 'PROCESSING',
                retry_count = retry_count + 1,
                updated_at = NOW()
            WHERE batch_no = #{batchNo}
              AND shard_index = #{shardIndex}
              AND status = 'PENDING'
            """)
    int claimIfPending(@Param("batchNo") String batchNo, @Param("shardIndex") Integer shardIndex);

    /**
     * 标记分片完成。
     */
    @Update("""
            UPDATE recalculation_shard
            SET status = 'DONE',
                updated_at = NOW()
            WHERE batch_no = #{batchNo}
              AND shard_index = #{shardIndex}
            """)
    int markDone(@Param("batchNo") String batchNo, @Param("shardIndex") Integer shardIndex);

    /**
     * 标记分片失败，等待 MQ 重投。
     */
    @Update("""
            UPDATE recalculation_shard
            SET status = 'PENDING',
                last_error_message = #{message},
                updated_at = NOW()
            WHERE batch_no = #{batchNo}
              AND shard_index = #{shardIndex}
            """)
    int markFailed(@Param("batchNo") String batchNo,
                   @Param("shardIndex") Integer shardIndex,
                   @Param("message") String message);
}
