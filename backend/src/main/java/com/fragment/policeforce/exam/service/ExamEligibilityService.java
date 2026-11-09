package com.fragment.policeforce.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.common.constants.ExamSlotStatusConstants;
import com.fragment.policeforce.common.constants.ExamSlotTypeConstants;
import com.fragment.policeforce.entity.ExamAppointment;
import com.fragment.policeforce.entity.ExamBatch;
import com.fragment.policeforce.entity.ExamSlot;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.exam.persistence.ExamAppointmentMapper;
import com.fragment.policeforce.exam.persistence.ExamSlotMapper;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 体检预约的业务校验器。
 *
 * <p>把「这条预约请求到底合不合法」的所有判断收敛在一处，
 * 热门通道（Redis 预占前）与普通通道（MySQL 扣减前）共用同一套规则，
 * 避免两条链路出现口径不一致。</p>
 */
@Service
@RequiredArgsConstructor
public class ExamEligibilityService {

    private final ExamSlotMapper slotMapper;
    private final PoliceOfficerMapper officerMapper;
    private final ExamAppointmentMapper appointmentMapper;

    /**
     * 校验一次体检预约请求。
     *
     * @return 校验失败的原因；通过时返回 null
     */
    public String check(Long slotId, Long officerId) {
        ExamSlot slot = slotMapper.selectById(slotId);
        if (slot == null) {
            return "体检时段不存在";
        }

        PoliceOfficer officer = officerMapper.selectById(officerId);
        if (officer == null) {
            return "警员不存在";
        }
        if (!"ACTIVE".equals(officer.getStatus())) {
            return "该警员当前不在编，无法预约体检";
        }

        LocalDateTime now = LocalDateTime.now();
        if (!ExamSlotStatusConstants.OPEN.equals(slot.getStatus())) {
            return "体检时段当前不可预约";
        }
        if (slot.getOpenTime() != null && now.isBefore(slot.getOpenTime())) {
            return "体检时段尚未放号";
        }
        if (slot.getEndDatetime() == null || !now.isBefore(slot.getEndDatetime())) {
            return "体检时段已结束";
        }
        return null;
    }

    /**
     * 校验批次是否在放号窗口内。
     */
    public String checkBatch(ExamBatch batch) {
        if (batch == null) {
            return "体检批次不存在";
        }
        LocalDateTime now = LocalDateTime.now();
        if (batch.getReleaseTime() != null && now.isBefore(batch.getReleaseTime())) {
            return "体检批次尚未放号";
        }
        if (batch.getCloseTime() != null && !now.isBefore(batch.getCloseTime())) {
            return "体检批次已结束";
        }
        return null;
    }

    /**
     * 判断时段是否属于热门（需要走 Redis 前置裁决）。
     */
    public boolean isHot(ExamSlot slot) {
        return slot != null && ExamSlotTypeConstants.HOT.equals(slot.getSlotType());
    }

    /**
     * 一人一档校验：该警员在该时段是否已持有有效预约。
     */
    public boolean hasActiveAppointment(Long officerId, Long slotId) {
        return appointmentMapper.selectCount(new LambdaQueryWrapper<ExamAppointment>()
                .eq(ExamAppointment::getOfficerId, officerId)
                .eq(ExamAppointment::getSlotId, slotId)
                .eq(ExamAppointment::getIsActive, 1)) > 0;
    }
}
