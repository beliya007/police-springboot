package com.fragment.policeforce.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fragment.policeforce.dto.DutySlotAddDTO;
import com.fragment.policeforce.dto.DutySlotPageQueryDTO;
import com.fragment.policeforce.dto.DutySlotUpdateDTO;
import com.fragment.policeforce.entity.DutySlot;

import java.util.List;

public interface DutySlotService extends IService<DutySlot> {

    List<DutySlot> getSlotsByStationId(Long stationId);

    DutySlot getStationSlotById(Long id);

    Page<DutySlot> pageStationSlot(DutySlotPageQueryDTO queryDTO);

    void addStationSlot(DutySlotAddDTO dto);

    void updateStationSlot(DutySlotUpdateDTO dto);

    void removeStationSlot(Long id);

    void deductQuotaIfAvailable(Long slotId);

    void restoreQuota(Long slotId);
}
