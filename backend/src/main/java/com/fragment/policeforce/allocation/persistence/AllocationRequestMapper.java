package com.fragment.policeforce.allocation.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.allocation.model.AllocationRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

public interface AllocationRequestMapper extends BaseMapper<AllocationRequest> {

    @Insert("""
            INSERT IGNORE INTO allocation_request
                (request_id, user_id, officer_id, station_id, slot_id, status, created_at)
            VALUES
                (#{request.requestId}, #{request.userId}, #{request.officerId},
                 #{request.stationId}, #{request.slotId}, #{request.status}, #{request.createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "request.id")
    int insertProcessingIgnore(@Param("request") AllocationRequest request);
}
