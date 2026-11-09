package com.fragment.policeforce.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fragment.policeforce.entity.ExamAppointment;
import com.fragment.policeforce.entity.ExamBatch;
import com.fragment.policeforce.entity.ExamSlot;
import com.fragment.policeforce.exam.api.dto.ExamPageQueryDTO;
import com.fragment.policeforce.exam.api.vo.ExamAppointmentVO;
import com.fragment.policeforce.exam.persistence.ExamAppointmentMapper;
import com.fragment.policeforce.exam.persistence.ExamSlotMapper;
import com.fragment.policeforce.mapper.ExamBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 体检预约查询服务。
 */
@Service
@RequiredArgsConstructor
public class ExamQueryService {

    private final ExamAppointmentMapper appointmentMapper;
    private final ExamSlotMapper slotMapper;
    private final ExamBatchMapper batchMapper;

    /**
     * 分页查询体检预约单。
     */
    public Page<ExamAppointmentVO> page(ExamPageQueryDTO queryDTO) {
        ExamPageQueryDTO actualQuery = queryDTO == null ? new ExamPageQueryDTO() : queryDTO;
        long pageNum = actualQuery.getPageNum() == null ? 1L : actualQuery.getPageNum();
        long pageSize = actualQuery.getPageSize() == null ? 10L : actualQuery.getPageSize();

        LambdaQueryWrapper<ExamAppointment> wrapper = new LambdaQueryWrapper<ExamAppointment>()
                .eq(actualQuery.getBatchId() != null, ExamAppointment::getBatchId, actualQuery.getBatchId())
                .eq(actualQuery.getSlotId() != null, ExamAppointment::getSlotId, actualQuery.getSlotId())
                .eq(actualQuery.getOfficerId() != null, ExamAppointment::getOfficerId, actualQuery.getOfficerId())
                .eq(StringUtils.hasText(actualQuery.getStatus()),
                        ExamAppointment::getStatus, actualQuery.getStatus())
                .orderByDesc(ExamAppointment::getId);

        Page<ExamAppointment> entityPage = appointmentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<ExamAppointmentVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 查询某警员在某时段的活跃预约。
     */
    public ExamAppointment findActive(Long officerId, Long slotId) {
        return appointmentMapper.selectActiveByOfficerAndSlot(officerId, slotId);
    }

    /**
     * 列出当前放号中的批次。
     */
    public List<ExamBatch> listReleasedBatches() {
        return batchMapper.selectReleased();
    }

    /**
     * 列出某批次的时段。
     */
    public List<ExamSlot> listSlots(Long batchId) {
        return slotMapper.selectList(new LambdaQueryWrapper<ExamSlot>()
                .eq(batchId != null, ExamSlot::getBatchId, batchId)
                .orderByAsc(ExamSlot::getStartDatetime));
    }

    private ExamAppointmentVO toVO(ExamAppointment appointment) {
        ExamAppointmentVO vo = new ExamAppointmentVO();
        BeanUtils.copyProperties(appointment, vo);
        vo.setFromWaiting(appointment.getFromWaiting() != null && appointment.getFromWaiting() == 1);
        return vo;
    }
}
