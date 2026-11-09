package com.fragment.policeforce.admin.api;

import com.fragment.policeforce.admin.dto.DutyModuleAddDTO;
import com.fragment.policeforce.admin.dto.DutyModuleUpdateDTO;
import com.fragment.policeforce.admin.dto.ModuleRatioUpdateDTO;
import com.fragment.policeforce.admin.service.DutyModuleAdminService;
import com.fragment.policeforce.common.auth.AdminOnly;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.entity.DutyModule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 勤务模块管理接口 —— 对应 read.txt 中
 * 「后台管理系统中各个模块的增删改查及比重设置」。
 */
@RestController
@RequestMapping("/module")
@RequiredArgsConstructor
public class DutyModuleController {

    private final DutyModuleAdminService moduleAdminService;

    /**
     * 查询全部模块（含停用的）。
     */
    @GetMapping("/list")
    public Result<List<DutyModule>> list() {
        return Result.success(moduleAdminService.listAll());
    }

    @AdminOnly
    @PostMapping
    public Result<DutyModule> add(@Valid @RequestBody DutyModuleAddDTO dto) {
        return Result.success(moduleAdminService.add(dto));
    }

    @AdminOnly
    @PutMapping
    public Result<Void> update(@Valid @RequestBody DutyModuleUpdateDTO dto) {
        moduleAdminService.update(dto);
        return Result.success();
    }

    @AdminOnly
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        moduleAdminService.remove(id);
        return Result.success();
    }

    /**
     * 批量调整模块比重。
     *
     * <p>比重调整会触发全量重算（异步），接口立即返回。</p>
     */
    @AdminOnly
    @PutMapping("/ratios")
    public Result<Void> updateRatios(@Valid @RequestBody ModuleRatioUpdateDTO dto) {
        moduleAdminService.updateRatios(dto);
        return Result.success();
    }
}
