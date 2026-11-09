package com.fragment.policeforce.allocation.service;

import com.fragment.policeforce.common.constants.DutySlotTypeConstants;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.entity.DutySlot;
import com.fragment.policeforce.allocation.api.dto.AllocationCreateDTO;
import com.fragment.policeforce.allocation.api.vo.AllocationSubmitVO;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.reminder.AllocationAutoCancelService;
import com.fragment.policeforce.allocation.reminder.AllocationReminderTaskService;
import com.fragment.policeforce.allocation.support.AllocationPersistenceHelper;
import com.fragment.policeforce.service.PoliceStationService;
import com.fragment.policeforce.service.DutySlotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NormalAllocationServiceTest {

    @Mock PoliceStationService policeStationService;
    @Mock DutySlotService slotService;
    @Mock AllocationPersistenceHelper persistenceHelper;
    @Mock AllocationReminderTaskService reminderService;
    @Mock AllocationAutoCancelService autoCancelService;
    @InjectMocks NormalAllocationService service;

    @Test
    void shouldCreateAllocationSynchronously() {
        PoliceStation station = new PoliceStation();
        station.setId(1L);
        station.setStationName("城东分局");
        DutySlot slot = new DutySlot();
        slot.setId(10L);
        slot.setStationId(1L);
        slot.setSlotType(DutySlotTypeConstants.NORMAL);
        slot.setStatus("OPEN");
        slot.setEndDatetime(LocalDateTime.now().plusHours(1));
        AllocationAssignment allocation = new AllocationAssignment();
        allocation.setId(88L);
        allocation.setAllocationNo("RES-88");

        when(policeStationService.getById(1L)).thenReturn(station);
        when(slotService.getById(10L)).thenReturn(slot);
        when(persistenceHelper.buildReservation(7L, 101L, 1L, 10L, station, slot)).thenReturn(allocation);

        AllocationSubmitVO result = service.create("request-id", 7L, 101L, 1L, 10L);

        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getAllocationId()).isEqualTo(88L);
        verify(slotService).deductQuotaIfAvailable(10L);
        verify(persistenceHelper).saveWithRetry(allocation);
        verify(reminderService).createBeforeStartReminder(allocation);
        verify(autoCancelService).schedule(allocation);
    }

    private AllocationCreateDTO dto() {
        AllocationCreateDTO dto = new AllocationCreateDTO();
        dto.setStationId(1L);
        dto.setSlotId(10L);
        return dto;
    }
}
