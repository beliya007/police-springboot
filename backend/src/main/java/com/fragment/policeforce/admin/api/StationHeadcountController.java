package com.fragment.policeforce.admin.api;

import com.fragment.policeforce.admin.dto.StationHeadcountUpdateDTO;
import com.fragment.policeforce.admin.service.StationHeadcountAdminService;
import com.fragment.policeforce.common.auth.AdminOnly;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.entity.StationModuleConfig;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 警局配置人数接口 —— 对应 read.txt 中
 * 「警局配置人数的展示界面」与「配置人数的详情展示页面」。
 */
@RestController
@RequestMapping("/station/headcount")
@RequiredArgsConstructor
public class StationHeadcountController {

    private final StationHeadcountAdminService headcountAdminService;

    /**
     * 查询某警局的配置人数明细。
     */
    @GetMapping("/list")
    public Result<List<StationModuleConfig>> list(@RequestParam Long stationId) {
        return Result.success(headcountAdminService.listByStation(stationId));
    }

    /**
     * 批量设置某警局的配置人数。保存后触发该警局的增量重算（异步）。
     */
    @AdminOnly
    @PutMapping
    public Result<Void> update(@Valid @RequestBody StationHeadcountUpdateDTO dto) {
        headcountAdminService.update(dto);
        return Result.success();
    }
}
