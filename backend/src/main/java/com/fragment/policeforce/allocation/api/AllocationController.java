package com.fragment.policeforce.allocation.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fragment.policeforce.common.auth.AdminOnly;
import com.fragment.policeforce.common.auth.LoginUser;
import com.fragment.policeforce.common.auth.UserContext;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.allocation.api.dto.AllocationCancelDTO;
import com.fragment.policeforce.allocation.api.dto.AllocationCreateDTO;
import com.fragment.policeforce.allocation.api.dto.AllocationPageQueryDTO;
import com.fragment.policeforce.allocation.service.AllocationCommandService;
import com.fragment.policeforce.allocation.service.AllocationQueryService;
import com.fragment.policeforce.allocation.api.vo.AllocationSubmitVO;
import com.fragment.policeforce.allocation.api.vo.AllocationVO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/allocation")
public class AllocationController {

    @Autowired
    private AllocationCommandService reservationCommandService;

    @Autowired
    private AllocationQueryService reservationQueryService;

    @GetMapping
    public Result<List<AllocationVO>> listMyReservations() {
        LoginUser loginUser = UserContext.requireUser();
        return Result.success(reservationQueryService.listByUserId(loginUser.getId()));
    }

    @AdminOnly
    @GetMapping("/page")
    public Result<Page<AllocationVO>> pageReservation(AllocationPageQueryDTO queryDTO) {
        return Result.success(reservationQueryService.page(queryDTO));
    }

    @GetMapping("/{id}")
    public Result<AllocationVO> getAllocationById(@PathVariable Long id) {
        LoginUser loginUser = UserContext.requireUser();
        return Result.success(
                reservationQueryService.getById(loginUser.getId(), loginUser.isAdmin(), id)
        );
    }

    @PostMapping
    public ResponseEntity<Result<AllocationSubmitVO>> createReservation(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AllocationCreateDTO dto) {
        AllocationSubmitVO result = reservationCommandService.create(
                UserContext.requireUser().getId(), idempotencyKey, dto);
        return "PENDING".equals(result.getStatus())
                ? ResponseEntity.accepted().body(Result.accepted(result))
                : ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/requests/{requestId}")
    public Result<AllocationSubmitVO> getAllocationRequest(@PathVariable String requestId) {
        return Result.success(reservationCommandService.getRequest(
                UserContext.requireUser().getId(), requestId));
    }

    @PutMapping("/{id}/check-in")
    public Result<Void> checkIn(@PathVariable Long id) {
        reservationCommandService.checkIn(UserContext.requireUser().getId(), id);
        return Result.success();
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancelReservation(@PathVariable Long id,
                                          @Valid @RequestBody AllocationCancelDTO dto) {
        reservationCommandService.cancel(UserContext.requireUser().getId(), id, dto);
        return Result.success();
    }
}
