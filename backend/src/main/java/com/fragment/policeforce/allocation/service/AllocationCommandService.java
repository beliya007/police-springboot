package com.fragment.policeforce.allocation.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fragment.policeforce.allocation.model.AllocationStatusConstants;
import com.fragment.policeforce.common.constants.DutySlotTypeConstants;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.allocation.redis.HotAllocationRedisService;
import com.fragment.policeforce.allocation.redis.AllocationRateLimiter;
import com.fragment.policeforce.allocation.reminder.AllocationAutoCancelService;
import com.fragment.policeforce.allocation.api.dto.AllocationCancelDTO;
import com.fragment.policeforce.allocation.api.dto.AllocationCreateDTO;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.entity.DutySlot;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.allocation.reminder.AllocationReminderTaskService;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import com.fragment.policeforce.service.DutySlotService;
import com.fragment.policeforce.allocation.api.vo.AllocationSubmitVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 预约命令入口
 */
@Service
public class AllocationCommandService {

    private final AllocationMapper reservationMapper;
    private final DutySlotService dutySlotService;
    private final PoliceOfficerMapper officerMapper;
    private final HotAllocationRedisService hotRedis;
    private final AllocationRateLimiter rateLimiter;
    private final AllocationReminderTaskService reminderService;
    private final AllocationAutoCancelService autoCancelService;
    private final HotAllocationRequestService hotRequestService;
    private final NormalAllocationService normalAllocationService;
    private final long checkInBeforeStartMinutes;

    public AllocationCommandService(AllocationMapper reservationMapper,
                                     DutySlotService dutySlotService,
                                     PoliceOfficerMapper officerMapper,
                                     HotAllocationRedisService hotRedis,
                                     AllocationRateLimiter rateLimiter,
                                     AllocationReminderTaskService reminderService,
                                     AllocationAutoCancelService autoCancelService,
                                     HotAllocationRequestService hotRequestService,
                                     NormalAllocationService normalAllocationService,
                                     @Value("${app.allocation.auto-cancel.check-in-before-start-minutes:30}") long checkInBeforeStartMinutes) {
        this.reservationMapper = reservationMapper;
        this.dutySlotService = dutySlotService;
        this.officerMapper = officerMapper;
        this.hotRedis = hotRedis;
        this.rateLimiter = rateLimiter;
        this.reminderService = reminderService;
        this.autoCancelService = autoCancelService;
        this.hotRequestService = hotRequestService;
        this.normalAllocationService = normalAllocationService;
        this.checkInBeforeStartMinutes = checkInBeforeStartMinutes;
    }

    /**
     * 预约统一入口：
     */
    public AllocationSubmitVO create(Long userId, String idempotencyKey, AllocationCreateDTO dto) {
        // 归一化 Idempotency-Key → 稳定 UUID。
        String requestId = hotRequestService.normalizeRequestId(idempotencyKey);

        // 警力调配的业务主体是「警员」。普通用户不传时按登录账号映射；
        // 管理员可以显式指定被调配的警员。
        Long officerId = dto.getOfficerId();
        if (officerId == null) {
            PoliceOfficer officer = officerMapper.selectByUserId(userId);
            if (officer == null) {
                throw new BusinessException(400, "当前账号未绑定警员档案，无法提交调配申请");
            }
            officerId = officer.getId();
        }

        if (hotRedis.isPreheatedHotSlot(dto.getSlotId())) {
            // 热门预约先限流
            rateLimiter.checkCreateAllocationLimit(userId, DutySlotTypeConstants.HOT);
            // 直接交给热门受理器
            return hotRequestService.accept(requestId, userId, officerId, dto.getStationId(), dto.getSlotId());
        }

        // 普通通道--前置校验
        DutySlot routeSlot = dutySlotService.getById(dto.getSlotId());
        if (routeSlot == null) {
            throw new BusinessException("时段不存在");
        }
        if (!dto.getStationId().equals(routeSlot.getStationId())) {
            throw new BusinessException("时段不属于当前资源");
        }

        if (DutySlotTypeConstants.HOT.equals(routeSlot.getSlotType())) {
            // 热门时段只走已预热的 Redis 快照，避免缓存缺失时高并发穿透到 MySQL。
            throw new BusinessException(503, "热门时段尚未完成预热，请稍后重试");
        }
        // 普通预约也限流（阈值不同），然后进入真正的 MySQL 事务扣减。
        rateLimiter.checkCreateAllocationLimit(userId, DutySlotTypeConstants.NORMAL);
        return normalAllocationService.create(requestId, userId, officerId, dto.getStationId(), dto.getSlotId());
    }

    /**
     * 按 requestId 查询本人预约请求的结果 委托给热门受理器
     */
    public AllocationSubmitVO getRequest(Long userId, String requestId) {
        return hotRequestService.getRequest(userId, requestId);
    }

    /**
     * 签到：用条件更新保证并发安全。
     */
    @Transactional(rollbackFor = Exception.class)
    public void checkIn(Long userId, Long id) {
        // 查预约，必须存在且属于自己。
        if (id == null) {
            throw new BusinessException("预约ID不能为空");
        }
        AllocationAssignment allocation = requireReservation(id);
        if (!allocation.getUserId().equals(userId)) {
            throw new BusinessException(403, "不能签到他人的预约");
        }
        // 已经签过就直接返回（幂等）。
        if (allocation.getCheckedInAt() != null) {
            return;
        }
        // 必须还是 BOOKED 状态才允许签到。
        if (!AllocationStatusConstants.BOOKED.equals(allocation.getStatus())) {
            throw new BusinessException("当前预约状态不允许签到");
        }
        // 签到时间窗口：不能太早（开始前 N 分钟才放），也不能太晚（过了自动取消截止）。
        validateCheckInWindow(allocation);

        LocalDateTime now = LocalDateTime.now();
        // 条件更新：只有"仍 BOOKED 且未签到"才能改成已签到，防止并发重复签到。
        int updated = reservationMapper.update(null, new LambdaUpdateWrapper<AllocationAssignment>()
                .eq(AllocationAssignment::getId, id)
                .eq(AllocationAssignment::getUserId, userId)
                .eq(AllocationAssignment::getStatus, AllocationStatusConstants.BOOKED)
                .isNull(AllocationAssignment::getCheckedInAt)
                .set(AllocationAssignment::getCheckedInAt, now)
                .set(AllocationAssignment::getUpdatedAt, now));
        if (updated <= 0) {
            // 更新 0 行：可能已被其他线程签到。再读一次确认，避免误报。
            AllocationAssignment latest = reservationMapper.selectById(id);
            if (latest != null && latest.getCheckedInAt() != null) {
                return;
            }
            throw new BusinessException("当前预约状态不允许签到");
        }
    }

    /**
     * 取消预约
     * 成功后：还 MySQL 名额、取消提醒任务、最后回补 Redis 热门时段的占位和库存。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long id, AllocationCancelDTO dto) {
        // 必须是自己的、存在的预约。
        AllocationAssignment allocation = requireReservation(id);
        if (!allocation.getUserId().equals(userId)) {
            throw new BusinessException("不能取消他人的预约");
        }
        // 可选的取消原因。
        String reason = StringUtils.hasText(dto.getCancelReason()) ? dto.getCancelReason().trim() : null;
        // 条件更新：只有 BOOKED 才允许取消，返回 0 行说明状态已变（如已签到/已取消）。
        int updated = reservationMapper.update(null, new LambdaUpdateWrapper<AllocationAssignment>()
                .eq(AllocationAssignment::getId, id)
                .eq(AllocationAssignment::getUserId, userId)
                .eq(AllocationAssignment::getStatus, AllocationStatusConstants.BOOKED)
                .set(AllocationAssignment::getStatus, AllocationStatusConstants.CANCELLED)
                .set(AllocationAssignment::getCancelReason, reason)
                .setSql("is_active = NULL"));
        if (updated <= 0) {
            throw new BusinessException("当前预约状态不允许取消");
        }
        // 还 MySQL 名额
        dutySlotService.restoreQuota(allocation.getSlotId());
        // 取消待触发的提醒任务。
        reminderService.cancelPendingByAllocationId(allocation.getId());
        // 如果是热门时段的预约：回补 Redis 里被占的位和库存
        hotRedis.releaseAfterSuccessfulCancellation(
                allocation.getSourceType(), allocation.getSlotId(), allocation.getOfficerId());
    }

    private AllocationAssignment requireReservation(Long id) {
        AllocationAssignment allocation = reservationMapper.selectById(id);
        if (allocation == null) {
            throw new BusinessException("预约记录不存在");
        }
        return allocation;
    }

    private void validateCheckInWindow(AllocationAssignment allocation) {
        LocalDateTime start = allocation.getSlotStartDatetime();
        LocalDateTime deadline = autoCancelService.resolveAutoCancelDeadline(allocation);
        if (start == null || deadline == null) {
            throw new BusinessException("预约时间不完整，无法签到");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(start.minusMinutes(Math.max(checkInBeforeStartMinutes, 0)))) {
            throw new BusinessException("未到签到时间");
        }
        if (now.isAfter(deadline)) {
            throw new BusinessException("签到已超时");
        }
    }

}
