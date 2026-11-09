package com.fragment.policeforce.allocation.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fragment.policeforce.common.outbox.MessageOutboxService;
import com.fragment.policeforce.allocation.messaging.AllocationCreateCommand;
import com.fragment.policeforce.allocation.support.AllocationPersistenceHelper;
import com.fragment.policeforce.allocation.messaging.AllocationResultEvent;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.model.AllocationRequest;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.entity.DutySlot;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.allocation.persistence.AllocationRequestMapper;
import com.fragment.policeforce.mapper.PoliceStationMapper;
import com.fragment.policeforce.mapper.DutySlotMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationConfirmationServiceTest {

    @Mock AllocationRequestMapper requestMapper;
    @Mock AllocationMapper reservationMapper;
    @Mock PoliceStationMapper policeStationMapper;
    @Mock DutySlotMapper slotMapper;
    @Mock AllocationPersistenceHelper persistenceHelper;
    @Mock MessageOutboxService outboxService;

    private AllocationConfirmationService service;

    @BeforeEach
    void setUp() {
        initTableInfo(AllocationRequest.class);
        service = new AllocationConfirmationService(
                requestMapper, reservationMapper, policeStationMapper, slotMapper,
                persistenceHelper, outboxService, "allocation-result");
    }

    @Test
    void successfulConfirmationShouldWriteOneTerminalLedgerAndResultOutbox() {
        AllocationCreateCommand command = command();
        PoliceStation station = station();
        DutySlot slot = slot();
        AllocationAssignment allocation = allocation();
        when(requestMapper.insertProcessingIgnore(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, AllocationRequest.class).setId(1L);
            return 1;
        });
        when(policeStationMapper.selectById(1L)).thenReturn(station);
        when(slotMapper.selectById(10L)).thenReturn(slot);
        when(persistenceHelper.buildReservation(7L, 101L, 1L, 10L, station, slot)).thenReturn(allocation);
        when(persistenceHelper.saveIfNoActiveConflict(allocation))
                .thenReturn(AllocationPersistenceHelper.SaveOutcome.CREATED);
        when(slotMapper.deductQuotaIfAvailable(10L)).thenReturn(1);
        when(requestMapper.updateById(any(AllocationRequest.class))).thenReturn(1);

        service.confirm(command);

        ArgumentCaptor<AllocationRequest> ledger = ArgumentCaptor.forClass(AllocationRequest.class);
        verify(requestMapper).updateById(ledger.capture());
        assertThat(ledger.getValue().getStatus()).isEqualTo("CONFIRMED");
        assertThat(ledger.getValue().getAllocationId()).isEqualTo(88L);
        verify(outboxService).enqueue(
                eq("RESERVATION_REQUEST"), eq(command.requestId()), eq("RESERVATION_RESULT"),
                eq("allocation-result"), eq("RESULT"), eq(command.requestId()), any(),
                any(AllocationResultEvent.class));
    }

    @Test
    void duplicateDeliveryAfterTerminalStateShouldAckWithoutNewWrites() {
        AllocationCreateCommand command = command();
        AllocationRequest existing = ledger("CONFIRMED");
        when(requestMapper.insertProcessingIgnore(any())).thenReturn(0);
        when(requestMapper.selectOne(any())).thenReturn(existing);

        service.confirm(command);

        verifyNoInteractions(policeStationMapper, slotMapper, persistenceHelper, outboxService);
        verify(requestMapper, never()).updateById(any(AllocationRequest.class));
    }

    @Test
    void exhaustedMysqlQuotaShouldRemoveTentativeAllocationAndCommitRejection() {
        AllocationCreateCommand command = command();
        PoliceStation station = station();
        DutySlot slot = slot();
        AllocationAssignment allocation = allocation();
        when(requestMapper.insertProcessingIgnore(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, AllocationRequest.class).setId(1L);
            return 1;
        });
        when(policeStationMapper.selectById(1L)).thenReturn(station);
        when(slotMapper.selectById(10L)).thenReturn(slot);
        when(persistenceHelper.buildReservation(7L, 101L, 1L, 10L, station, slot)).thenReturn(allocation);
        when(persistenceHelper.saveIfNoActiveConflict(allocation))
                .thenReturn(AllocationPersistenceHelper.SaveOutcome.CREATED);
        when(slotMapper.deductQuotaIfAvailable(10L)).thenReturn(0);
        when(requestMapper.updateById(any(AllocationRequest.class))).thenReturn(1);

        service.confirm(command);

        verify(reservationMapper).deleteById(88L);
        ArgumentCaptor<AllocationRequest> ledger = ArgumentCaptor.forClass(AllocationRequest.class);
        verify(requestMapper).updateById(ledger.capture());
        assertThat(ledger.getValue().getStatus()).isEqualTo("REJECTED");
        assertThat(ledger.getValue().getRejectCode()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void technicalFailureShouldEscapeForRocketMqRetry() {
        AllocationCreateCommand command = command();
        when(requestMapper.insertProcessingIgnore(any())).thenReturn(1);
        when(policeStationMapper.selectById(1L)).thenThrow(new IllegalStateException("database timeout"));

        assertThatThrownBy(() -> service.confirm(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database timeout");

        verify(requestMapper, never()).updateById(any(AllocationRequest.class));
        verifyNoInteractions(outboxService);
    }

    private AllocationCreateCommand command() {
        return new AllocationCreateCommand(
                "33c8aa68-9fe0-4d30-afd5-6e62de86bd6c", 7L, 101L, 1L, 10L);
    }

    private AllocationRequest ledger(String status) {
        AllocationRequest request = new AllocationRequest();
        request.setRequestId(command().requestId());
        request.setUserId(7L);
        request.setOfficerId(101L);
        request.setStationId(1L);
        request.setSlotId(10L);
        request.setStatus(status);
        return request;
    }

    private PoliceStation station() {
        PoliceStation station = new PoliceStation();
        station.setId(1L);
        station.setStationName("实验室");
        return station;
    }

    private DutySlot slot() {
        DutySlot slot = new DutySlot();
        slot.setId(10L);
        slot.setStationId(1L);
        slot.setSlotType("HOT");
        slot.setStatus("OPEN");
        slot.setOpenTime(LocalDateTime.now().minusMinutes(1));
        slot.setEndDatetime(LocalDateTime.now().plusHours(1));
        return slot;
    }

    private AllocationAssignment allocation() {
        AllocationAssignment allocation = new AllocationAssignment();
        allocation.setId(88L);
        allocation.setAllocationNo("RES-88");
        return allocation;
    }

    private void initTableInfo(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(type.getName());
        TableInfoHelper.initTableInfo(assistant, type);
    }
}
