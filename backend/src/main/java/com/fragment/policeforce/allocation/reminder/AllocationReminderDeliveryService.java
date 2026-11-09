package com.fragment.policeforce.allocation.reminder;

import com.fragment.policeforce.allocation.model.AllocationReminderTask;
import com.fragment.policeforce.allocation.reminder.AllocationReminderTaskService;
import com.fragment.policeforce.service.UserNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AllocationReminderDeliveryService {

    private static final String STATUS_PENDING = "PENDING";

    private final AllocationReminderTaskService reservationReminderTaskService;
    private final UserNotificationService userNotificationService;

    public AllocationReminderDeliveryService(AllocationReminderTaskService reservationReminderTaskService,
                                              UserNotificationService userNotificationService) {
        this.reservationReminderTaskService = reservationReminderTaskService;
        this.userNotificationService = userNotificationService;
    }

    public void deliver(Long reminderTaskId) {
        if (reminderTaskId == null) {
            return;
        }

        AllocationReminderTask task = reservationReminderTaskService.getById(reminderTaskId);
        if (task == null || !STATUS_PENDING.equals(task.getStatus())) {
            return;
        }

        try {
            userNotificationService.createReminderNotification(task);
            reservationReminderTaskService.markSent(task);
        } catch (DuplicateKeyException duplicateKeyException) {
            reservationReminderTaskService.markSent(task);
            log.info("AllocationAssignment reminder task already delivered, treating as success. taskId={}", task.getId());
        } catch (Exception exception) {
            reservationReminderTaskService.markRetryFailure(task, exception.getMessage());
            throw exception;
        }
    }
}
