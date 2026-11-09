package com.fragment.policeforce.allocation.reminder;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.model.AllocationReminderTask;

public interface AllocationReminderTaskService extends IService<AllocationReminderTask> {

    void createBeforeStartReminder(AllocationAssignment allocation);

    void cancelPendingByAllocationId(Long allocationId);

    void markSent(AllocationReminderTask task);

    void markRetryFailure(AllocationReminderTask task, String errorMessage);
}
