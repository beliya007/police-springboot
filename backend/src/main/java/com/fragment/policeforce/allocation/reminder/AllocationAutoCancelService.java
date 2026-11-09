package com.fragment.policeforce.allocation.reminder;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fragment.policeforce.allocation.model.AllocationStatusConstants;
import com.fragment.policeforce.allocation.reminder.messaging.DelayMessageEventTypes;
import com.fragment.policeforce.allocation.reminder.messaging.DelayMessageTags;
import com.fragment.policeforce.allocation.reminder.messaging.AllocationAutoCancelDelayPayload;
import com.fragment.policeforce.common.outbox.MessageOutboxService;
import com.fragment.policeforce.allocation.redis.HotAllocationRedisService;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.service.DutySlotService;
import com.fragment.policeforce.service.UserNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AllocationAutoCancelService {

    public static final String AUTO_CANCEL_REASON = "超时未签到自动取消";

    private final AllocationMapper reservationMapper;
    private final DutySlotService dutySlotService;
    private final HotAllocationRedisService hotAllocationRedisService;
    private final UserNotificationService userNotificationService;
    private final MessageOutboxService messageOutboxService;
    private final String delayTopic;
    private final boolean enabled;
    private final long graceMinutes;

    public AllocationAutoCancelService(AllocationMapper reservationMapper,
                                        DutySlotService dutySlotService,
                                        HotAllocationRedisService hotAllocationRedisService,
                                        UserNotificationService userNotificationService,
                                        MessageOutboxService messageOutboxService,
                                        @Value("${app.message-outbox.delay-topic:allocation-delay}") String delayTopic,
                                        @Value("${app.allocation.auto-cancel.enabled:true}") boolean enabled,
                                        @Value("${app.allocation.auto-cancel.grace-minutes:15}") long graceMinutes) {
        this.reservationMapper = reservationMapper;
        this.dutySlotService = dutySlotService;
        this.hotAllocationRedisService = hotAllocationRedisService;
        this.userNotificationService = userNotificationService;
        this.messageOutboxService = messageOutboxService;
        this.delayTopic = delayTopic;
        this.enabled = enabled;
        this.graceMinutes = Math.max(graceMinutes, 0);
    }

    public void fillAutoCancelDeadline(AllocationAssignment allocation) {
        if (!enabled || allocation == null || allocation.getSlotStartDatetime() == null) {
            return;
        }
        allocation.setAutoCancelDeadline(resolveAutoCancelDeadline(allocation));
    }

    public void schedule(AllocationAssignment allocation) {
        if (!enabled
                || allocation == null
                || allocation.getId() == null
                || allocation.getAutoCancelDeadline() == null) {
            return;
        }

        messageOutboxService.enqueue(
                "RESERVATION",
                String.valueOf(allocation.getId()),
                DelayMessageEventTypes.RESERVATION_AUTO_CANCEL,
                delayTopic,
                DelayMessageTags.tagFor(DelayMessageEventTypes.RESERVATION_AUTO_CANCEL),
                String.valueOf(allocation.getId()),
                allocation.getAutoCancelDeadline(),
                new AllocationAutoCancelDelayPayload(allocation.getId())
        );
    }

    public LocalDateTime resolveAutoCancelDeadline(AllocationAssignment allocation) {
        if (allocation == null) {
            return null;
        }
        if (allocation.getAutoCancelDeadline() != null) {
            return allocation.getAutoCancelDeadline();
        }
        if (allocation.getSlotStartDatetime() == null) {
            return null;
        }
        return allocation.getSlotStartDatetime().plusMinutes(graceMinutes);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean autoCancel(Long allocationId) {
        if (!enabled || allocationId == null) {
            return false;
        }

        AllocationAssignment allocation = reservationMapper.selectById(allocationId);
        if (allocation == null
                || !AllocationStatusConstants.BOOKED.equals(allocation.getStatus())
                || allocation.getCheckedInAt() != null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = resolveAutoCancelDeadline(allocation);
        if (deadline != null && now.isBefore(deadline)) {
            return false;
        }

        int updatedRows = reservationMapper.update(null, new LambdaUpdateWrapper<AllocationAssignment>()
                .eq(AllocationAssignment::getId, allocationId)
                .eq(AllocationAssignment::getStatus, AllocationStatusConstants.BOOKED)
                .isNull(AllocationAssignment::getCheckedInAt)
                .set(AllocationAssignment::getStatus, AllocationStatusConstants.CANCELLED)
                .set(AllocationAssignment::getCancelReason, AUTO_CANCEL_REASON)
                .set(AllocationAssignment::getUpdatedAt, now)
                .setSql("is_active = NULL"));
        if (updatedRows <= 0) {
            return false;
        }

        dutySlotService.restoreQuota(allocation.getSlotId());
        hotAllocationRedisService.releaseAfterSuccessfulCancellation(
                allocation.getSourceType(),
                allocation.getSlotId(),
                allocation.getUserId()
        );
        userNotificationService.createAutoCancelNotification(allocation);
        return true;
    }
}
