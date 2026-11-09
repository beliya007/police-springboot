package com.fragment.policeforce.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fragment.policeforce.common.auth.AdminOnly;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.dto.PoliceStationAddDTO;
import com.fragment.policeforce.dto.PoliceStationPageQueryDTO;
import com.fragment.policeforce.dto.PoliceStationQueryDTO;
import com.fragment.policeforce.dto.PoliceStationUpdateDTO;
import com.fragment.policeforce.service.PoliceStationService;
import com.fragment.policeforce.vo.PoliceStationVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/station")
public class PoliceStationController {

    @Autowired
    private PoliceStationService policeStationService;

    @GetMapping("/list")
    public Result<List<PoliceStationVO>> list(PoliceStationQueryDTO queryDTO) {
        return Result.success(policeStationService.search(queryDTO));
    }

    @GetMapping("/page")
    public Result<Page<PoliceStationVO>> pageResource(PoliceStationPageQueryDTO queryDTO) {
        return Result.success(policeStationService.pageResource(queryDTO));
    }

    @GetMapping("/{id}")
    public Result<PoliceStationVO> getStationById(@PathVariable Long id) {
        return Result.success(policeStationService.getStationById(id));
    }

    @AdminOnly
    @PostMapping
    public Result<Void> addResource(@Valid @RequestBody PoliceStationAddDTO dto) {
        policeStationService.addResource(dto);
        return Result.success();
    }

    @AdminOnly
    @PutMapping
    public Result<Void> updateResource(@Valid @RequestBody PoliceStationUpdateDTO dto) {
        policeStationService.updateResource(dto);
        return Result.success();
    }

    @AdminOnly
    @DeleteMapping("/{id}")
    public Result<Void> removeResource(@PathVariable Long id) {
        policeStationService.removeResource(id);
        return Result.success();
    }
}
