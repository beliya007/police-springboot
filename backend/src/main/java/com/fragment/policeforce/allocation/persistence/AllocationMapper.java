package com.fragment.policeforce.allocation.persistence;

import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
* @author fragment
* @description 针对表【allocation(预约记录表)】的数据库操作Mapper
* @createDate 2026-03-28 15:02:51
* @Entity com.fragment.policeforce.allocation.model.AllocationAssignment
*/
public interface AllocationMapper extends BaseMapper<AllocationAssignment> {

    int insertIgnore(@Param("allocation") AllocationAssignment allocation);
}




