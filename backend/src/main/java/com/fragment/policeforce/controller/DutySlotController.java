package com.fragment.policeforce.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fragment.policeforce.common.auth.AdminOnly;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.dto.DutySlotAddDTO;
import com.fragment.policeforce.dto.DutySlotPageQueryDTO;
import com.fragment.policeforce.dto.DutySlotUpdateDTO;
import com.fragment.policeforce.entity.DutySlot;
import com.fragment.policeforce.service.DutySlotService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/station-slot")
public class DutySlotController {

    @Autowired
    private DutySlotService dutySlotService;

    @GetMapping("/list")
    public Result<List<DutySlot>> listByResource(@RequestParam Long stationId) {
        return Result.success(dutySlotService.getSlotsByStationId(stationId));
    }

    @GetMapping("/{id}")
    public Result<DutySlot> getStationSlotById(@PathVariable Long id) {
        return Result.success(dutySlotService.getStationSlotById(id));
    }

    @GetMapping("/page")
    public Result<Page<DutySlot>> pageStationSlot(DutySlotPageQueryDTO queryDTO) {
        return Result.success(dutySlotService.pageStationSlot(queryDTO));
    }

    @AdminOnly
    @PostMapping
    public Result<Void> addStationSlot(@Valid @RequestBody DutySlotAddDTO dto) {
        dutySlotService.addStationSlot(dto);
        return Result.success();
    }

    @AdminOnly
    @PutMapping
    public Result<Void> updateStationSlot(@Valid @RequestBody DutySlotUpdateDTO dto) {
        dutySlotService.updateStationSlot(dto);
        return Result.success();
    }

    @AdminOnly
    @DeleteMapping("/{id}")
    public Result<Void> removeStationSlot(@PathVariable Long id) {
        dutySlotService.removeStationSlot(id);
        return Result.success();
    }
}
