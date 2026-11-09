package com.fragment.policeforce.admin.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fragment.policeforce.admin.dto.PoliceOfficerAddDTO;
import com.fragment.policeforce.admin.dto.PoliceOfficerUpdateDTO;
import com.fragment.policeforce.admin.service.PoliceOfficerAdminService;
import com.fragment.policeforce.common.auth.AdminOnly;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.entity.PoliceOfficer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/**
 * 警员信息管理接口 —— 对应 read.txt 中
 * 「后台管理系统中各警局的人员信息的管理」。
 */
@RestController
@RequestMapping("/officer")
@RequiredArgsConstructor
public class PoliceOfficerController {

    private final PoliceOfficerAdminService officerAdminService;

    /**
     * 列出某警局的警员。
     */
    @GetMapping("/list")
    public Result<List<PoliceOfficer>> listByStation(@RequestParam(required = false) Long stationId) {
        return Result.success(officerAdminService.listByStation(stationId));
    }

    /**
     * 分页查询警员。
     */
    @GetMapping("/page")
    public Result<Page<PoliceOfficer>> page(@RequestParam(required = false) Long stationId,
                                             @RequestParam(required = false) Long moduleId,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "1") Long pageNum,
                                             @RequestParam(defaultValue = "10") Long pageSize) {
        return Result.success(officerAdminService.page(
                stationId, moduleId, keyword, status, pageNum, pageSize));
    }

    @AdminOnly
    @PostMapping
    public Result<PoliceOfficer> add(@Valid @RequestBody PoliceOfficerAddDTO dto) {
        return Result.success(officerAdminService.add(dto));
    }

    @AdminOnly
    @PutMapping
    public Result<Void> update(@Valid @RequestBody PoliceOfficerUpdateDTO dto) {
        officerAdminService.update(dto);
        return Result.success();
    }

    @AdminOnly
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        officerAdminService.remove(id);
        return Result.success();
    }
}
