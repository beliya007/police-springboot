package com.fragment.policeforce.allocation.service;

import com.fragment.policeforce.common.constants.DutySlotTypeConstants;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.allocation.redis.HotAllocationRedisService;
import com.fragment.policeforce.allocation.redis.AllocationRateLimiter;
import com.fragment.policeforce.allocation.reminder.AllocationAutoCancelService;
import com.fragment.policeforce.allocation.api.dto.AllocationCreateDTO;
import com.fragment.policeforce.entity.DutySlot;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.allocation.reminder.AllocationReminderTaskService;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import com.fragment.policeforce.service.DutySlotService;
import com.fragment.policeforce.allocation.api.vo.AllocationSubmitVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationCommandServiceTest {

    private static final String REQUEST_ID = "33c8aa68-9fe0-4d30-afd5-6e62de86bd6c";
    private static final Long OFFICER_ID = 101L;

    @Mock AllocationMapper reservationMapper;
    @Mock DutySlotService slotService;
    @Mock PoliceOfficerMapper officerMapper;
    @Mock HotAllocationRedisService hotRedis;
    @Mock AllocationRateLimiter rateLimiter;
    @Mock AllocationReminderTaskService reminderService;
    @Mock AllocationAutoCancelService autoCancelService;
    @Mock HotAllocationRequestService hotRequestService;
    @Mock NormalAllocationService normalAllocationService;

    private AllocationCommandService service;

    @BeforeEach
    void setUp() {
        lenient().when(hotRequestService.normalizeRequestId(REQUEST_ID)).thenReturn(REQUEST_ID);
        service = new AllocationCommandService(
                reservationMapper, slotService, officerMapper, hotRedis, rateLimiter,
                reminderService, autoCancelService, hotRequestService,
                normalAllocationService, 30);
    }

    @Test
    void normalSlotShouldRemainSynchronous() {
        DutySlot slot = slot(DutySlotTypeConstants.NORMAL);
        AllocationSubmitVO confirmed = new AllocationSubmitVO();
        confirmed.setRequestId(REQUEST_ID);
        confirmed.setStatus("CONFIRMED");
        confirmed.setAllocationId(88L);

        when(hotRedis.isPreheatedHotSlot(10L)).thenReturn(false);
        when(slotService.getById(10L)).thenReturn(slot);
        when(normalAllocationService.create(REQUEST_ID, 7L, OFFICER_ID, 1L, 10L))
                .thenReturn(confirmed);

        AllocationSubmitVO result = service.create(7L, REQUEST_ID, dto());

        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getAllocationId()).isEqualTo(88L);
        verify(normalAllocationService).create(REQUEST_ID, 7L, OFFICER_ID, 1L, 10L);
    }

    @Test
    void preheatedHotSlotShouldBeAcceptedWithoutDatabaseAccess() {
        AllocationSubmitVO pending = new AllocationSubmitVO();
        pending.setRequestId(REQUEST_ID);
        pending.setStatus("PENDING");
        when(hotRedis.isPreheatedHotSlot(10L)).thenReturn(true);
        when(hotRequestService.accept(REQUEST_ID, 7L, OFFICER_ID, 1L, 10L)).thenReturn(pending);

        AllocationSubmitVO result = service.create(7L, REQUEST_ID, dto());

        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(rateLimiter).checkCreateAllocationLimit(7L, DutySlotTypeConstants.HOT);
        verifyNoInteractions(reservationMapper, normalAllocationService);
        verify(slotService, never()).getById(any());
    }

    @Test
    void missingHotSnapshotShouldFailClosed() {
        when(hotRedis.isPreheatedHotSlot(10L)).thenReturn(false);
        when(slotService.getById(10L)).thenReturn(slot(DutySlotTypeConstants.HOT));

        assertThatThrownBy(() -> service.create(7L, REQUEST_ID, dto()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(503));

        verifyNoInteractions(reservationMapper, normalAllocationService);
    }

    @Test
    void malformedIdempotencyKeyShouldFailBeforeRouting() {
        when(hotRequestService.normalizeRequestId("bad"))
                .thenThrow(new BusinessException(400, "Idempotency-Key 必须是 UUID"));

        assertThatThrownBy(() -> service.create(7L, "bad", dto()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UUID");

        verifyNoInteractions(hotRedis, slotService, reservationMapper, normalAllocationService);
    }

    @Test
    void userWithoutOfficerProfileShouldBeRejectedBeforeRouting() {
        AllocationCreateDTO dto = new AllocationCreateDTO();
        dto.setStationId(1L);
        dto.setSlotId(10L);
        when(officerMapper.selectByUserId(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(7L, REQUEST_ID, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("警员档案");

        verifyNoInteractions(hotRedis, slotService, normalAllocationService);
    }

    private AllocationCreateDTO dto() {
        AllocationCreateDTO dto = new AllocationCreateDTO();
        dto.setOfficerId(OFFICER_ID);
        dto.setStationId(1L);
        dto.setSlotId(10L);
        return dto;
    }

    private DutySlot slot(String type) {
        DutySlot slot = new DutySlot();
        slot.setId(10L);
        slot.setStationId(1L);
        slot.setSlotType(type);
        slot.setStatus("OPEN");
        slot.setOpenTime(LocalDateTime.now().minusMinutes(1));
        slot.setStartDatetime(LocalDateTime.now().plusHours(1));
        slot.setEndDatetime(LocalDateTime.now().plusHours(2));
        return slot;
    }
}
