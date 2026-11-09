package com.fragment.policeforce.allocation.service;

import com.fragment.policeforce.allocation.redis.HotAllocationRedisService;
import com.fragment.policeforce.allocation.reminder.AllocationAutoCancelService;
import com.fragment.policeforce.allocation.messaging.AllocationResultEvent;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.allocation.reminder.AllocationReminderTaskService;
import com.fragment.policeforce.service.UserNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationResultServiceTest {

    @Mock HotAllocationRedisService hotRedis;
    @Mock AllocationMapper reservationMapper;
    @Mock AllocationReminderTaskService reminderService;
    @Mock AllocationAutoCancelService autoCancelService;
    @Mock UserNotificationService notificationService;

    private AllocationResultService service;

    @BeforeEach
    void setUp() {
        service = new AllocationResultService(
                hotRedis, reservationMapper, reminderService, autoCancelService, notificationService);
    }

    @Test
    void confirmedResultShouldScheduleFollowUpOnlyAfterAllocationExists() {
        AllocationAssignment allocation = new AllocationAssignment();
        allocation.setId(88L);
        AllocationResultEvent event = event("CONFIRMED", 88L);
        when(reservationMapper.selectById(88L)).thenReturn(allocation);

        service.handle("event-1", event);

        verify(hotRedis).complete(event);
        verify(notificationService).createAllocationResultNotification("event-1", event);
        verify(reminderService).createBeforeStartReminder(allocation);
        verify(autoCancelService).schedule(allocation);
    }

    @Test
    void rejectedResultShouldReleaseThroughRedisWithoutSchedulingSuccessTasks() {
        AllocationResultEvent event = event("REJECTED", null);

        service.handle("event-2", event);

        verify(hotRedis).complete(event);
        verify(notificationService).createAllocationResultNotification("event-2", event);
        verify(reservationMapper, never()).selectById(org.mockito.ArgumentMatchers.any());
        verify(reminderService, never()).createBeforeStartReminder(org.mockito.ArgumentMatchers.any());
        verify(autoCancelService, never()).schedule(org.mockito.ArgumentMatchers.any());
    }

    private AllocationResultEvent event(String status, Long allocationId) {
        return new AllocationResultEvent(
                "33c8aa68-9fe0-4d30-afd5-6e62de86bd6c",
                7L, 101L, 1L, 10L, status, allocationId,
                allocationId == null ? null : "RES-88",
                allocationId == null ? "OUT_OF_STOCK" : null,
                allocationId == null ? "时段余量不足" : null);
    }
}
