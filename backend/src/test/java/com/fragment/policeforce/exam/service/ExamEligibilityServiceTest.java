package com.fragment.policeforce.exam.service;

import com.fragment.policeforce.common.constants.ExamSlotTypeConstants;
import com.fragment.policeforce.entity.ExamAppointment;
import com.fragment.policeforce.entity.ExamBatch;
import com.fragment.policeforce.entity.ExamSlot;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.exam.persistence.ExamAppointmentMapper;
import com.fragment.policeforce.exam.persistence.ExamSlotMapper;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamEligibilityServiceTest {

    @Mock ExamSlotMapper slotMapper;
    @Mock PoliceOfficerMapper officerMapper;
    @Mock ExamAppointmentMapper appointmentMapper;

    private ExamEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new ExamEligibilityService(slotMapper, officerMapper, appointmentMapper);
    }

    @Test
    void missingSlotShouldReject() {
        when(slotMapper.selectById(10L)).thenReturn(null);

        assertThat(service.check(10L, 100L)).isEqualTo("体检时段不存在");
    }

    @Test
    void missingOfficerShouldReject() {
        when(slotMapper.selectById(10L)).thenReturn(openSlot());
        when(officerMapper.selectById(100L)).thenReturn(null);

        assertThat(service.check(10L, 100L)).isEqualTo("警员不存在");
    }

    @Test
    void inactiveOfficerShouldReject() {
        when(slotMapper.selectById(10L)).thenReturn(openSlot());
        PoliceOfficer officer = new PoliceOfficer();
        officer.setStatus("INACTIVE");
        when(officerMapper.selectById(100L)).thenReturn(officer);

        assertThat(service.check(10L, 100L)).contains("不在编");
    }

    @Test
    void closedSlotShouldReject() {
        ExamSlot slot = openSlot();
        slot.setStatus("CLOSED");
        when(slotMapper.selectById(10L)).thenReturn(slot);
        PoliceOfficer officer = new PoliceOfficer();
        officer.setStatus("ACTIVE");
        when(officerMapper.selectById(100L)).thenReturn(officer);

        assertThat(service.check(10L, 100L)).contains("不可预约");
    }

    @Test
    void slotNotYetReleasedShouldReject() {
        ExamSlot slot = openSlot();
        slot.setOpenTime(LocalDateTime.now().plusHours(1));
        when(slotMapper.selectById(10L)).thenReturn(slot);
        PoliceOfficer officer = new PoliceOfficer();
        officer.setStatus("ACTIVE");
        when(officerMapper.selectById(100L)).thenReturn(officer);

        assertThat(service.check(10L, 100L)).contains("尚未放号");
    }

    @Test
    void expiredSlotShouldReject() {
        ExamSlot slot = openSlot();
        slot.setEndDatetime(LocalDateTime.now().minusMinutes(1));
        when(slotMapper.selectById(10L)).thenReturn(slot);
        PoliceOfficer officer = new PoliceOfficer();
        officer.setStatus("ACTIVE");
        when(officerMapper.selectById(100L)).thenReturn(officer);

        assertThat(service.check(10L, 100L)).contains("已结束");
    }

    @Test
    void validRequestShouldPass() {
        when(slotMapper.selectById(10L)).thenReturn(openSlot());
        PoliceOfficer officer = new PoliceOfficer();
        officer.setStatus("ACTIVE");
        when(officerMapper.selectById(100L)).thenReturn(officer);

        assertThat(service.check(10L, 100L)).isNull();
    }

    @Test
    void batchBeforeReleaseTimeShouldReject() {
        ExamBatch batch = new ExamBatch();
        batch.setReleaseTime(LocalDateTime.now().plusHours(1));

        assertThat(service.checkBatch(batch)).contains("尚未放号");
    }

    @Test
    void batchAfterCloseTimeShouldReject() {
        ExamBatch batch = new ExamBatch();
        batch.setCloseTime(LocalDateTime.now().minusMinutes(1));

        assertThat(service.checkBatch(batch)).contains("已结束");
    }

    @Test
    void hotSlotShouldBeDetected() {
        ExamSlot slot = openSlot();
        slot.setSlotType(ExamSlotTypeConstants.HOT);

        assertThat(service.isHot(slot)).isTrue();
        assertThat(service.isHot(null)).isFalse();
    }

    @Test
    void existingActiveAppointmentShouldBlockDuplicateBooking() {
        when(appointmentMapper.selectCount(any())).thenReturn(1L);

        assertThat(service.hasActiveAppointment(100L, 10L)).isTrue();
    }

    private ExamSlot openSlot() {
        ExamSlot slot = new ExamSlot();
        slot.setId(10L);
        slot.setBatchId(1L);
        slot.setSlotType(ExamSlotTypeConstants.NORMAL);
        slot.setStatus("OPEN");
        slot.setOpenTime(LocalDateTime.now().minusMinutes(1));
        slot.setStartDatetime(LocalDateTime.now().plusHours(1));
        slot.setEndDatetime(LocalDateTime.now().plusHours(2));
        slot.setTotalQuota(70);
        slot.setRemainQuota(70);
        return slot;
    }
}
