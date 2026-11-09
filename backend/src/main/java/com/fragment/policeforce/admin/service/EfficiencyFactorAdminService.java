package com.fragment.policeforce.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.admin.dto.EfficiencyFactorUpdateDTO;
import com.fragment.policeforce.common.audit.AdminAuditHelper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.EfficiencyFactor;
import com.fragment.policeforce.efficiency.persistence.EfficiencyFactorMapper;
import com.fragment.policeforce.efficiency.service.EfficiencyConstants;
import com.fragment.policeforce.efficiency.service.RecalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 效能因素权重管理服务 —— 对应 read.txt 中
 * 「后台管理系统中效能因素（年龄，性别）比重的设置页面」。
 *
 * <p><strong>权重和约束</strong>：同一维度下所有取值的权重之和必须等于 100。
 * 这个约束在保存时就同步校验（不能等到异步重算才发现），
 * 因为权重非法会让所有警局的效能都算错。</p>
 *
 * <p>保存成功后触发全量重算 —— 权重是全局输入，
 * 任何一个警局的效能都会受影响。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EfficiencyFactorAdminService {

    private final EfficiencyFactorMapper factorMapper;
    private final RecalculationService recalculationService;
    private final AdminAuditHelper adminAuditHelper;

    /**
     * 列出全部因素配置。
     */
    public List<EfficiencyFactor> listAll() {
        return factorMapper.selectList(new LambdaQueryWrapper<EfficiencyFactor>()
                .orderByAsc(EfficiencyFactor::getFactorType)
                .orderByAsc(EfficiencyFactor::getId));
    }

    /**
     * 按维度列出因素配置。
     */
    public List<EfficiencyFactor> listByType(String factorType) {
        return factorMapper.selectList(new LambdaQueryWrapper<EfficiencyFactor>()
                .eq(EfficiencyFactor::getFactorType, factorType)
                .orderByAsc(EfficiencyFactor::getId));
    }

    /**
     * 整组更新某个维度的因素权重。
     *
     * <p>「整组更新」而不是逐条更新：预检要求权重和恰好 100，
     * 逐条更新必然出现中间态（比如先设成 60，再设成 40），
     * 整组提交才能保证「要么全对，要么全不改」。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(EfficiencyFactorUpdateDTO dto) {
        String factorType = dto.getFactorType().trim().toUpperCase();
        if (!EfficiencyConstants.FACTOR_TYPE_AGE.equals(factorType)
                && !EfficiencyConstants.FACTOR_TYPE_GENDER.equals(factorType)) {
            throw new BusinessException(400, "因素维度只能是 AGE 或 GENDER");
        }

        adminAuditHelper.execute(
                "EFFICIENCY_FACTOR",
                "UPDATE",
                "EFFICIENCY_FACTOR",
                () -> null,
                () -> "factorType=" + factorType + ", items=" + dto.getItems().size(),
                () -> {
                    Set<String> values = new HashSet<>();
                    int total = 0;
                    for (EfficiencyFactorUpdateDTO.Item item : dto.getItems()) {
                        String factorValue = item.getFactorValue().trim();
                        if (!values.add(factorValue)) {
                            throw new BusinessException(400, "因素配置中存在重复的取值：" + factorValue);
                        }
                        int weight = item.getWeight() == null ? 0 : item.getWeight();
                        if (weight < 0 || weight > 100) {
                            throw new BusinessException(400, "权重必须在 0-100 之间");
                        }
                        total += weight;

                        EfficiencyFactor factor = item.getId() == null
                                ? null : factorMapper.selectById(item.getId());
                        if (factor == null) {
                            factor = new EfficiencyFactor();
                            factor.setFactorType(factorType);
                            factor.setFactorValue(factorValue);
                            factor.setWeight(weight);
                            factor.setInjuryPenalty(item.getInjuryPenalty() == null ? 0 : item.getInjuryPenalty());
                            factor.setStatus(StringUtils.hasText(item.getStatus()) ? item.getStatus() : "ENABLED");
                            factor.setCreatedAt(LocalDateTime.now());
                            factor.setUpdatedAt(LocalDateTime.now());
                            if (factorMapper.insert(factor) <= 0) {
                                throw new BusinessException("新增效能因素失败");
                            }
                        } else {
                            factor.setFactorType(factorType);
                            factor.setFactorValue(factorValue);
                            factor.setWeight(weight);
                            factor.setInjuryPenalty(item.getInjuryPenalty() == null ? 0 : item.getInjuryPenalty());
                            if (StringUtils.hasText(item.getStatus())) {
                                factor.setStatus(item.getStatus());
                            }
                            factor.setUpdatedAt(LocalDateTime.now());
                            if (factorMapper.updateById(factor) <= 0) {
                                throw new BusinessException("更新效能因素失败");
                            }
                        }
                    }
                    if (total != EfficiencyConstants.FACTOR_WEIGHT_TOTAL) {
                        throw new BusinessException(400,
                                "同一维度下所有取值的权重之和必须为 100，当前为 " + total);
                    }
                    if (EfficiencyConstants.FACTOR_TYPE_GENDER.equals(factorType)
                            && !values.contains("MALE") && !values.contains("FEMALE")) {
                        throw new BusinessException(400, "性别维度必须同时配置 MALE 与 FEMALE");
                    }
                }
        );

        // 权重是全局输入 → 全量重算（异步）。
        try {
            recalculationService.trigger(
                    EfficiencyConstants.TRIGGER_FACTOR_WEIGHT,
                    EfficiencyConstants.SCOPE_ALL,
                    null, null, null);
        } catch (RuntimeException exception) {
            log.warn("Failed to trigger recalculation after factor weight change. reason={}",
                    exception.getMessage());
        }
    }

    /**
     * 启停某个因素配置。
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, String status) {
        EfficiencyFactor factor = factorMapper.selectById(id);
        if (factor == null) {
            throw new BusinessException("效能因素不存在");
        }
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : "ENABLED";
        if (!"ENABLED".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new BusinessException(400, "状态只能是 ENABLED 或 DISABLED");
        }
        adminAuditHelper.execute(
                "EFFICIENCY_FACTOR",
                "TOGGLE",
                "EFFICIENCY_FACTOR",
                () -> id,
                () -> "factorType=" + factor.getFactorType() + ", factorValue=" + factor.getFactorValue(),
                () -> {
                    factor.setStatus(normalized);
                    factor.setUpdatedAt(LocalDateTime.now());
                    if (factorMapper.updateById(factor) <= 0) {
                        throw new BusinessException("更新效能因素状态失败");
                    }
                }
        );
        try {
            recalculationService.trigger(
                    EfficiencyConstants.TRIGGER_FACTOR_WEIGHT,
                    EfficiencyConstants.SCOPE_ALL,
                    null, null, null);
        } catch (RuntimeException exception) {
            log.warn("Failed to trigger recalculation after factor toggle. reason={}", exception.getMessage());
        }
    }
}
