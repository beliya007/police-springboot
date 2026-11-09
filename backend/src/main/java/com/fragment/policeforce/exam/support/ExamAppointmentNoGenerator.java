package com.fragment.policeforce.exam.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Component;

/**
 * 体检预约单号生成器。
 */
@Component
public class ExamAppointmentNoGenerator {

    /**
     * 生成体检预约单号：EX + 雪花 ID，全局唯一且趋势递增。
     */
    public String next() {
        return "EX" + IdWorker.getIdStr();
    }
}
