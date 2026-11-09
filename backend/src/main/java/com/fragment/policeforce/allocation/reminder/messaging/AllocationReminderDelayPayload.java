package com.fragment.policeforce.allocation.reminder.messaging;

import lombok.Data;

@Data
public class AllocationReminderDelayPayload {

    private Long reminderTaskId;

    public AllocationReminderDelayPayload() {
    }

    public AllocationReminderDelayPayload(Long reminderTaskId) {
        this.reminderTaskId = reminderTaskId;
    }
}
