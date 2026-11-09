package com.fragment.policeforce.dashboard.api;

import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.dashboard.service.DashboardService;
import com.fragment.policeforce.dashboard.vo.PoliceForceOverviewVO;
import com.fragment.policeforce.dashboard.vo.StationOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 警力看板接口 —— 对应 read.txt 的三个展示页面。
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 警力规划总览：全局配置人数、有效警力、实际效能、缺编排行。
     */
    @GetMapping("/overview")
    public Result<PoliceForceOverviewVO> overview() {
        return Result.success(dashboardService.overview());
    }

    /**
     * 各警局的配置人数与实际效能列表。
     *
     * @param district 区域过滤，可为空
     */
    @GetMapping("/stations")
    public Result<List<StationOverviewVO>> stations(@RequestParam(required = false) String district) {
        return Result.success(dashboardService.listStations(district));
    }

    /**
     * 单警局详情：配置人数 + 实际效能 + 各模块占比及人数分布。
     */
    @GetMapping("/stations/{stationId}")
    public Result<StationOverviewVO> stationDetail(@PathVariable Long stationId) {
        return Result.success(dashboardService.detail(stationId));
    }
}
