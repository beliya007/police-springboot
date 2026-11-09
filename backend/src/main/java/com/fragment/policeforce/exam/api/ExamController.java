package com.fragment.policeforce.exam.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fragment.policeforce.common.auth.AdminOnly;
import com.fragment.policeforce.common.auth.LoginUser;
import com.fragment.policeforce.common.auth.UserContext;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.entity.ExamBatch;
import com.fragment.policeforce.entity.ExamSlot;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.exam.api.dto.ExamCancelDTO;
import com.fragment.policeforce.exam.api.dto.ExamCreateDTO;
import com.fragment.policeforce.exam.api.dto.ExamPageQueryDTO;
import com.fragment.policeforce.exam.api.dto.ExamResultRecordDTO;
import com.fragment.policeforce.exam.api.vo.ExamAppointmentVO;
import com.fragment.policeforce.exam.api.vo.ExamSubmitVO;
import com.fragment.policeforce.exam.service.ExamAppointmentService;
import com.fragment.policeforce.exam.service.ExamCancelService;
import com.fragment.policeforce.exam.service.ExamQueryService;
import com.fragment.policeforce.exam.service.ExamResultService;
import com.fragment.policeforce.exam.service.ExamWaitingService;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 体检预约接口。
 *
 * <p>热门时段的预约走「Redis Lua 原子预占 + RocketMQ 事务半消息」，
 * 接口立即返回 <strong>202 Accepted</strong>，
 * 客户端用同一个 {@code Idempotency-Key} 轮询 {@code /exam/requests/{requestId}} 取最终结果。</p>
 */
@RestController
@RequestMapping("/exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamAppointmentService appointmentService;
    private final ExamCancelService cancelService;
    private final ExamResultService resultService;
    private final ExamWaitingService waitingService;
    private final ExamQueryService queryService;
    private final PoliceOfficerMapper officerMapper;

    /**
     * 提交体检预约。
     */
    @PostMapping("/appointments")
    public ResponseEntity<Result<ExamSubmitVO>> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExamCreateDTO dto) {
        LoginUser loginUser = UserContext.requireUser();
        Long officerId = resolveOfficerId(loginUser, dto.getOfficerId());

        ExamSubmitVO result = appointmentService.create(idempotencyKey, officerId, dto.getSlotId());
        return "PENDING".equals(result.getStatus())
                ? ResponseEntity.accepted().body(Result.accepted(result))
                : ResponseEntity.ok(Result.success(result));
    }

    /**
     * 查询本人体检预约请求的最终结果。
     */
    @GetMapping("/requests/{requestId}")
    public Result<ExamSubmitVO> getRequest(@PathVariable String requestId) {
        LoginUser loginUser = UserContext.requireUser();
        Long officerId = resolveOfficerId(loginUser, null);
        return Result.success(appointmentService.getRequest(officerId, requestId));
    }

    /**
     * 取消体检预约。
     */
    @PutMapping("/appointments/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody ExamCancelDTO dto) {
        LoginUser loginUser = UserContext.requireUser();
        Long officerId = resolveOfficerId(loginUser, null);
        cancelService.cancel(officerId, id, dto.getCancelReason());
        return Result.success();
    }

    /**
     * 查询某体检时段的候补人数。
     */
    @GetMapping("/slots/{slotId}/waiting-count")
    public Result<Long> waitingCount(@PathVariable Long slotId) {
        return Result.success(waitingService.waitingCount(slotId));
    }

    /**
     * 列出当前放号中的体检批次。
     */
    @GetMapping("/batches")
    public Result<List<ExamBatch>> listBatches() {
        return Result.success(queryService.listReleasedBatches());
    }

    /**
     * 列出某批次的体检时段。
     */
    @GetMapping("/batches/{batchId}/slots")
    public Result<List<ExamSlot>> listSlots(@PathVariable Long batchId) {
        return Result.success(queryService.listSlots(batchId));
    }

    /**
     * 分页查询体检预约单（管理员）。
     */
    @AdminOnly
    @GetMapping("/appointments/page")
    public Result<Page<ExamAppointmentVO>> pageAppointments(ExamPageQueryDTO queryDTO) {
        return Result.success(queryService.page(queryDTO));
    }

    /**
     * 录入体检结论（管理员）。录入后立即触发受影响警局的增量重算。
     */
    @AdminOnly
    @PostMapping("/appointments/{id}/result")
    public Result<?> recordResult(@PathVariable Long id, @Valid @RequestBody ExamResultRecordDTO dto) {
        LoginUser loginUser = UserContext.requireUser();
        return Result.success(resultService.record(id, dto.getConclusion(), dto.getConclusionNote(),
                loginUser.getUsername()));
    }

    /**
     * 解析操作人对应的警员 ID。
     *
     * <p>普通用户只能操作自己的预约：警员由登录账号唯一确定。
     * 管理员可以显式指定警员（用于代录体检结论等后台操作）。</p>
     */
    private Long resolveOfficerId(LoginUser loginUser, Long requestedOfficerId) {
        if (loginUser.isAdmin() && requestedOfficerId != null) {
            return requestedOfficerId;
        }
        PoliceOfficer officer = officerMapper.selectByUserId(loginUser.getId());
        if (officer == null) {
            throw new BusinessException(403, "当前账号未绑定警员档案，无法进行体检预约操作");
        }
        return officer.getId();
    }
}
