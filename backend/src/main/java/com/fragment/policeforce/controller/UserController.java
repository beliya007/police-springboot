package com.fragment.policeforce.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fragment.policeforce.common.auth.AdminOnly;
import com.fragment.policeforce.common.auth.UserContext;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.allocation.api.dto.AllocationPageQueryDTO;
import com.fragment.policeforce.dto.UserAddDTO;
import com.fragment.policeforce.dto.UserPageQueryDTO;
import com.fragment.policeforce.dto.UserStatusUpdateDTO;
import com.fragment.policeforce.dto.UserUpdateDTO;
import com.fragment.policeforce.service.SysUserService;
import com.fragment.policeforce.allocation.service.AllocationQueryService;
import com.fragment.policeforce.allocation.api.vo.AllocationVO;
import com.fragment.policeforce.allocation.api.vo.UserAllocationOverviewVO;
import com.fragment.policeforce.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AdminOnly
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private AllocationQueryService reservationQueryService;

    @GetMapping("/page")
    public Result<Page<UserVO>> page(UserPageQueryDTO queryDTO) {
        return Result.success(sysUserService.pageUsers(queryDTO));
    }

    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.success(sysUserService.getUserById(id));
    }

    @GetMapping("/{id}/overview")
    public Result<UserAllocationOverviewVO> getAllocationOverview(@PathVariable Long id) {
        return Result.success(reservationQueryService.getUserOverview(id));
    }

    @GetMapping("/{id}/allocations")
    public Result<Page<AllocationVO>> pageUserReservations(@PathVariable Long id,
                                                            AllocationPageQueryDTO queryDTO) {
        return Result.success(reservationQueryService.pageByUser(id, queryDTO));
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody UserAddDTO dto) {
        sysUserService.addUser(dto);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody UserUpdateDTO dto) {
        sysUserService.updateUser(dto, UserContext.requireUser().getId());
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusUpdateDTO dto) {
        sysUserService.updateUserStatus(id, dto.getStatus(), UserContext.requireUser().getId());
        return Result.success();
    }

    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        sysUserService.resetPassword(id);
        return Result.success();
    }
}
