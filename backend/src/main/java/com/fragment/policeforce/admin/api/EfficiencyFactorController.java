package com.fragment.policeforce.admin.api;

import com.fragment.policeforce.admin.dto.EfficiencyFactorUpdateDTO;
import com.fragment.policeforce.admin.service.EfficiencyFactorAdminService;
import com.fragment.policeforce.common.auth.AdminOnly;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.entity.EfficiencyFactor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 效能因素权重设置接口 —— 对应 read.txt 中
 * 「后台管理系统中效能因素（年龄，性别）比重的设置页面」。
 */
@RestController
@RequestMapping("/factor")
@RequiredArgsConstructor
public class EfficiencyFactorController {

    private final EfficiencyFactorAdminService factorAdminService;

    /**
     * 查询全部因素配置。
     */
    @GetMapping("/list")
    public Result<List<EfficiencyFactor>> list(@RequestParam(required = false) String factorType) {
        return Result.success(factorType == null || factorType.isBlank()
                ? factorAdminService.listAll()
                : factorAdminService.listByType(factorType));
    }

    /**
     * 整组更新某个维度的权重。
     *
     * <p>同步校验「同一维度权重和 = 100」，通过后触发全量重算（异步）。</p>
     */
    @AdminOnly
    @PutMapping
    public Result<Void> update(@Valid @RequestBody EfficiencyFactorUpdateDTO dto) {
        factorAdminService.update(dto);
        return Result.success();
    }

    /**
     * 启停某个因素配置。
     */
    @AdminOnly
    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam String status) {
        factorAdminService.toggleStatus(id, status);
        return Result.success();
    }
}
