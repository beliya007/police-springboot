package com.fragment.policeforce.allocation.service;

import com.fragment.policeforce.common.constants.DutySlotStatusConstants;
import com.fragment.policeforce.common.constants.DutySlotTypeConstants;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.entity.DutySlot;
import com.fragment.policeforce.allocation.api.vo.AllocationSubmitVO;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.reminder.AllocationAutoCancelService;
import com.fragment.policeforce.allocation.reminder.AllocationReminderTaskService;
import com.fragment.policeforce.allocation.support.AllocationPersistenceHelper;
import com.fragment.policeforce.service.PoliceStationService;
import com.fragment.policeforce.service.DutySlotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 普通预约的同步事务：校验、扣减名额、落预约记录和创建后续任务。 */
@Service
public class NormalAllocationService {

    private final PoliceStationService policeStationService;
    private final DutySlotService slotService;
    private final AllocationPersistenceHelper persistenceHelper;
    private final AllocationReminderTaskService reminderService;
    private final AllocationAutoCancelService autoCancelService;

    public NormalAllocationService(PoliceStationService policeStationService,
                                    DutySlotService slotService,
                                    AllocationPersistenceHelper persistenceHelper,
                                    AllocationReminderTaskService reminderService,
                                    AllocationAutoCancelService autoCancelService) {
        this.policeStationService = policeStationService;
        this.slotService = slotService;
        this.persistenceHelper = persistenceHelper;
        this.reminderService = reminderService;
        this.autoCancelService = autoCancelService;
    }

    @Transactional(rollbackFor = Exception.class)
    public AllocationSubmitVO create(String requestId, Long userId, Long officerId,
                                     Long stationId, Long slotId) {
        PoliceStation station = policeStationService.getById(stationId);
        DutySlot slot = slotService.getById(slotId);
        validate(station, slot, stationId);
        slotService.deductQuotaIfAvailable(slot.getId());
        AllocationAssignment allocation = persistenceHelper.buildReservation(
                userId, officerId, station.getId(), slot.getId(), station, slot);
        persistenceHelper.saveWithRetry(allocation);
        reminderService.createBeforeStartReminder(allocation);
        autoCancelService.schedule(allocation);
        return confirmed(requestId, allocation);
    }

    private void validate(PoliceStation station, DutySlot slot, Long stationId) {
        if (station == null) {
            throw new BusinessException("资源不存在");
        }
        if (slot == null) {
            throw new BusinessException("时段不存在");
        }
        if (!stationId.equals(slot.getStationId())) {
            throw new BusinessException("时段不属于当前资源");
        }
        if (DutySlotTypeConstants.HOT.equals(slot.getSlotType())) {
            throw new BusinessException(503, "热门时段必须通过 Redis 预约通道");
        }
        if (!DutySlotStatusConstants.OPEN.equals(slot.getStatus())) {
            throw new BusinessException("当前时段不可预约");
        }
        if (slot.getEndDatetime() == null || !LocalDateTime.now().isBefore(slot.getEndDatetime())) {
            throw new BusinessException("当前时段已结束");
        }
    }

    private AllocationSubmitVO confirmed(String requestId, AllocationAssignment allocation) {
        AllocationSubmitVO result = new AllocationSubmitVO();
        result.setRequestId(requestId);
        result.setStatus(AllocationRequestStatus.CONFIRMED.name());
        result.setAllocationId(allocation.getId());
        result.setAllocationNo(allocation.getAllocationNo());
        result.setMessage("预约成功");
        return result;
    }
}
