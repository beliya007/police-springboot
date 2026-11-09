package com.fragment.policeforce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fragment.policeforce.entity.ExamBatch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 体检批次 Mapper。
 */
public interface ExamBatchMapper extends BaseMapper<ExamBatch> {

    /**
     * 查询处于放号窗口内的批次。
     */
    @Select("""
            SELECT * FROM exam_batch
            WHERE status = 'RELEASED'
              AND (release_time IS NULL OR release_time <= NOW())
              AND (close_time IS NULL OR close_time > NOW())
            ORDER BY release_time DESC
            """)
    List<ExamBatch> selectReleased();
}
