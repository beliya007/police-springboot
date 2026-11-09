package com.fragment.policeforce.allocation.reminder;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fragment.policeforce.allocation.model.AllocationStatusConstants;
import com.fragment.policeforce.common.constants.DutySlotTypeConstants;
import com.fragment.policeforce.allocation.reminder.messaging.DelayMessageEventTypes;
import com.fragment.policeforce.allocation.reminder.messaging.AllocationAutoCancelDelayPayload;
import com.fragment.policeforce.common.outbox.MessageOutboxService;
import com.fragment.policeforce.allocation.redis.HotAllocationRedisService;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.service.DutySlotService;
import com.fragment.policeforce.service.UserNotificationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationAutoCancelServiceTest {

    @Mock
    private AllocationMapper reservationMapper;
    @Mock
    private DutySlotService dutySlotService;
    @Mock
    private HotAllocationRedisService hotAllocationRedisService;
    @Mock
    private UserNotificationService userNotificationService;
    @Mock
    private MessageOutboxService messageOutboxService;

    private AllocationAutoCancelService autoCancelService;

    @BeforeEach
    void setUp() {
        initTableInfo(AllocationAssignment.class);
        autoCancelService = new AllocationAutoCancelService(
                reservationMapper,
                dutySlotService,
                hotAllocationRedisService,
                userNotificationService,
                messageOutboxService,
                "allocation-delay",
                true,
                15
        );
    }

    @Test
    void fillDeadlineAndScheduleShouldEnqueueAutoCancelDelayMessage() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        AllocationAssignment allocation = new AllocationAssignment();
        allocation.setId(88L);
        allocation.setSlotStartDatetime(start);

        autoCancelService.fillAutoCancelDeadline(allocation);
        autoCancelService.schedule(allocation);

        assertThat(allocation.getAutoCancelDeadline()).isEqualTo(start.plusMinutes(15));
        ArgumentCaptor<AllocationAutoCancelDelayPayload> payloadCaptor =
                ArgumentCaptor.forClass(AllocationAutoCancelDelayPayload.class);
        verify(messageOutboxService).enqueue(
                eq("RESERVATION"),
                eq("88"),
                eq(DelayMessageEventTypes.RESERVATION_AUTO_CANCEL),
                eq("allocation-delay"),
                eq("allocation-auto-cancel"),
                eq("88"),
                eq(start.plusMinutes(15)),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue().getAllocationId()).isEqualTo(88L);
    }

    @Test
    void autoCancelShouldCancelUncheckedBookedAllocationAndReleaseQuota() {
        AllocationAssignment allocation = bookedReservation();
        when(reservationMapper.selectById(88L)).thenReturn(allocation);
        when(reservationMapper.update(eq(null), any())).thenReturn(1);

        boolean cancelled = autoCancelService.autoCancel(88L);

        assertThat(cancelled).isTrue();
        verify(dutySlotService).restoreQuota(10L);
        verify(hotAllocationRedisService).releaseAfterSuccessfulCancellation(DutySlotTypeConstants.HOT, 10L, 7L);
        verify(userNotificationService).createAutoCancelNotification(allocation);
    }

    @Test
    void autoCancelShouldSkipWhenAllocationAlreadyCheckedIn() {
        AllocationAssignment allocation = bookedReservation();
        allocation.setCheckedInAt(LocalDateTime.now().minusMinutes(1));
        when(reservationMapper.selectById(88L)).thenReturn(allocation);

        boolean cancelled = autoCancelService.autoCancel(88L);

        assertThat(cancelled).isFalse();
        verify(reservationMapper, never()).update(eq(null), any());
        verify(dutySlotService, never()).restoreQuota(any());
        verify(userNotificationService, never()).createAutoCancelNotification(any());
    }

    @Test
    void autoCancelShouldSkipWhenDeadlineIsNotReached() {
        AllocationAssignment allocation = bookedReservation();
        allocation.setAutoCancelDeadline(LocalDateTime.now().plusMinutes(5));
        when(reservationMapper.selectById(88L)).thenReturn(allocation);

        boolean cancelled = autoCancelService.autoCancel(88L);

        assertThat(cancelled).isFalse();
        verify(reservationMapper, never()).update(eq(null), any());
        verify(dutySlotService, never()).restoreQuota(any());
        verify(userNotificationService, never()).createAutoCancelNotification(any());
    }

    @Test
    void autoCancelShouldSkipWhenAllocationAlreadyCancelled() {
        AllocationAssignment allocation = bookedReservation();
        allocation.setStatus(AllocationStatusConstants.CANCELLED);
        when(reservationMapper.selectById(88L)).thenReturn(allocation);

        boolean cancelled = autoCancelService.autoCancel(88L);

        assertThat(cancelled).isFalse();
        verify(reservationMapper, never()).update(eq(null), any());
        verify(dutySlotService, never()).restoreQuota(any());
        verify(userNotificationService, never()).createAutoCancelNotification(any());
    }

    private AllocationAssignment bookedReservation() {
        AllocationAssignment allocation = new AllocationAssignment();
        allocation.setId(88L);
        allocation.setUserId(7L);
        allocation.setStationId(1L);
        allocation.setSlotId(10L);
        allocation.setStatus(AllocationStatusConstants.BOOKED);
        allocation.setSourceType(DutySlotTypeConstants.HOT);
        allocation.setSlotStartDatetime(LocalDateTime.now().minusMinutes(20));
        allocation.setAutoCancelDeadline(LocalDateTime.now().minusMinutes(5));
        return allocation;
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }

        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
