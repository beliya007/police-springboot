package com.fragment.policeforce.efficiency.api;

import com.fragment.policeforce.common.auth.AdminOnly;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.efficiency.service.EfficiencyConstants;
import com.fragment.policeforce.efficiency.service.EfficiencyPrecheckService;
import com.fragment.policeforce.efficiency.service.RecalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 效能重算接口。
 *
 * <p>这是「202 Accepted + 异步分片重算」的对外入口：
 * 同步只做参数预检，预检通过后立即返回 202，
 * 真正的计算在后台由 RocketMQ 分片消息驱动。</p>
 */
@RestController
@RequestMapping("/efficiency")
@RequiredArgsConstructor
public class EfficiencyController {

    private final RecalculationService recalculationService;
    private final EfficiencyPrecheckService precheckService;

    /**
     * 触发效能重算。
     *
     * <p>返回 <strong>202 Accepted</strong>，响应体里带上
     * {@code requestId / batchNo / calcVersion}，
     * 客户端用 {@code requestId} 轮询 {@code /efficiency/requests/{requestId}} 查进度。</p>
     *
     * @param scopeType 重算范围：ALL / DISTRICT / STATION
     * @param scopeId   范围目标 ID（STATION 时为警局 ID）
     * @param district  区域名（DISTRICT 时使用）
     */
    @AdminOnly
    @PostMapping("/recalculate")
    public ResponseEntity<Result<RecalculationService.RecalcSubmitVO>> recalculate(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(defaultValue = EfficiencyConstants.SCOPE_ALL) String scopeType,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(required = false) String district) {
        RecalculationService.RecalcSubmitVO result = recalculationService.trigger(
                EfficiencyConstants.TRIGGER_MANUAL, scopeType, scopeId, district, idempotencyKey);
        return ResponseEntity.accepted().body(Result.accepted(result));
    }

    /**
     * 查询重算批次状态。
     */
    @GetMapping("/requests/{requestId}")
    public Result<RecalculationService.RecalcSubmitVO> status(@PathVariable String requestId) {
        return Result.success(recalculationService.status(requestId));
    }

    /**
     * 参数预检结果。用于后台在提交权重/比重前先看看能不能算。
     */
    @GetMapping("/precheck")
    public Result<EfficiencyPrecheckService.PrecheckResult> precheck() {
        return Result.success(precheckService.check());
    }
}
