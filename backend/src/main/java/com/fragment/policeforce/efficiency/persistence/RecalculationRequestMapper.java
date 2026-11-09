package com.fragment.policeforce.efficiency.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.RecalculationRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 效能重算请求账本 Mapper。
 *
 * <p>{@code insertProcessingIgnore} 是消费端双重幂等的数据库层保障：
 * 依赖 {@code uk_recalculation_request_id} 唯一键，重复投递直接返回 0。</p>
 */
public interface RecalculationRequestMapper extends BaseMapper<RecalculationRequest> {

    @Insert("""
            INSERT IGNORE INTO recalculation_request
                (request_id, batch_no, trigger_source, scope_type, scope_id,
                 calc_version, status, created_at)
            VALUES
                (#{requestId}, #{batchNo}, #{triggerSource}, #{scopeType}, #{scopeId},
                 #{calcVersion}, 'PROCESSING', NOW())
            """)
    int insertProcessingIgnore(@Param("requestId") String requestId,
                               @Param("batchNo") String batchNo,
                               @Param("triggerSource") String triggerSource,
                               @Param("scopeType") String scopeType,
                               @Param("scopeId") Long scopeId,
                               @Param("calcVersion") Long calcVersion);
}
