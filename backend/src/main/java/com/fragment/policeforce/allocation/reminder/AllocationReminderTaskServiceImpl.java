package com.fragment.policeforce.allocation.reminder;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fragment.policeforce.allocation.reminder.messaging.DelayMessageEventTypes;
import com.fragment.policeforce.allocation.reminder.messaging.DelayMessageTags;
import com.fragment.policeforce.allocation.reminder.messaging.AllocationReminderDelayPayload;
import com.fragment.policeforce.allocation.model.AllocationStatusConstants;
import com.fragment.policeforce.common.outbox.MessageOutboxService;
import com.fragment.policeforce.common.util.TruncateUtil;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.model.AllocationReminderTask;
import com.fragment.policeforce.allocation.persistence.AllocationReminderTaskMapper;
import com.fragment.policeforce.allocation.reminder.AllocationReminderTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class AllocationReminderTaskServiceImpl extends ServiceImpl<AllocationReminderTaskMapper, AllocationReminderTask>
        implements AllocationReminderTaskService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String REMIND_TYPE_BEFORE_START = "BEFORE_START";

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final boolean enabled;
    private final long beforeStartMinutes;
    private final MessageOutboxService messageOutboxService;
    private final String delayTopic;

    public AllocationReminderTaskServiceImpl(@Value("${app.allocation.reminder.enabled:true}") boolean enabled,
                                              @Value("${app.allocation.reminder.before-start-minutes:10}") long beforeStartMinutes,
                                              @Value("${app.message-outbox.delay-topic:allocation-delay}") String delayTopic,
                                              MessageOutboxService messageOutboxService) {
        this.enabled = enabled;
        this.beforeStartMinutes = beforeStartMinutes;
        this.delayTopic = delayTopic;
        this.messageOutboxService = messageOutboxService;
    }

    @Override
    public void createBeforeStartReminder(AllocationAssignment allocation) {
        if (!enabled || allocation == null || allocation.getId() == null) {
            return;
        }
        if (!AllocationStatusConstants.BOOKED.equals(allocation.getStatus())) {
            return;
        }
        if (allocation.getSlotStartDatetime() == null || !allocation.getSlotStartDatetime().isAfter(LocalDateTime.now())) {
            return;
        }

        AllocationReminderTask task = new AllocationReminderTask();
        task.setAllocationId(allocation.getId());
        task.setUserId(allocation.getUserId());
        task.setStationId(allocation.getStationId());
        task.setSlotId(allocation.getSlotId());
        task.setRemindType(REMIND_TYPE_BEFORE_START);
        task.setTitle("预约即将开始");
        task.setContent(buildReminderContent(allocation));
        task.setPlanSendTime(calculatePlanSendTime(allocation.getSlotStartDatetime()));
        task.setStatus(STATUS_PENDING);
        task.setRetryCount(0);
        try {
            this.save(task);
        } catch (DuplicateKeyException duplicate) {
            return;
        }
        messageOutboxService.enqueue(
                "RESERVATION_REMINDER_TASK",
                String.valueOf(task.getId()),
                DelayMessageEventTypes.RESERVATION_REMINDER,
                delayTopic,
                DelayMessageTags.tagFor(DelayMessageEventTypes.RESERVATION_REMINDER),
                String.valueOf(task.getId()),
                task.getPlanSendTime(),
                new AllocationReminderDelayPayload(task.getId())
        );
    }

    @Override
    public void cancelPendingByAllocationId(Long allocationId) {
        if (!enabled || allocationId == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AllocationReminderTask> updateWrapper = new LambdaUpdateWrapper<AllocationReminderTask>()
                .eq(AllocationReminderTask::getAllocationId, allocationId)
                .eq(AllocationReminderTask::getStatus, STATUS_PENDING)
                .set(AllocationReminderTask::getStatus, STATUS_CANCELLED)
                .set(AllocationReminderTask::getUpdatedAt, now)
                .set(AllocationReminderTask::getLastErrorMessage, "allocation cancelled");
        this.update(updateWrapper);
    }

    @Override
    public void markSent(AllocationReminderTask task) {
        if (task == null || task.getId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AllocationReminderTask> updateWrapper = new LambdaUpdateWrapper<AllocationReminderTask>()
                .eq(AllocationReminderTask::getId, task.getId())
                .eq(AllocationReminderTask::getStatus, STATUS_PENDING)
                .set(AllocationReminderTask::getStatus, STATUS_SENT)
                .set(AllocationReminderTask::getSentAt, now)
                .set(AllocationReminderTask::getUpdatedAt, now)
                .set(AllocationReminderTask::getLastErrorMessage, null);
        this.update(updateWrapper);
    }

    @Override
    public void markRetryFailure(AllocationReminderTask task, String errorMessage) {
        if (task == null || task.getId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AllocationReminderTask> updateWrapper = new LambdaUpdateWrapper<AllocationReminderTask>()
                .eq(AllocationReminderTask::getId, task.getId())
                .eq(AllocationReminderTask::getStatus, STATUS_PENDING)
                .set(AllocationReminderTask::getRetryCount, task.getRetryCount() == null ? 1 : task.getRetryCount() + 1)
                .set(AllocationReminderTask::getLastErrorMessage, TruncateUtil.truncate(errorMessage, 512))
                .set(AllocationReminderTask::getUpdatedAt, now);
        this.update(updateWrapper);
    }

    private LocalDateTime calculatePlanSendTime(LocalDateTime startDatetime) {
        LocalDateTime planSendTime = startDatetime.minusMinutes(Math.max(beforeStartMinutes, 0));
        LocalDateTime now = LocalDateTime.now();
        return planSendTime.isBefore(now) ? now : planSendTime;
    }

    private String buildReminderContent(AllocationAssignment allocation) {
        String stationName = StringUtils.hasText(allocation.getStationName()) ? allocation.getStationName() : "您预约的资源";
        return "您预约的资源“" + stationName + "”将在 "
                + allocation.getSlotStartDatetime().format(DATETIME_FORMATTER)
                + " 开始，请按时到场。";
    }

}
