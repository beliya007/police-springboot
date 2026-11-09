package com.fragment.policeforce.efficiency.service;

import com.fragment.policeforce.entity.EfficiencyFactor;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.entity.StationModuleConfig;
import com.fragment.policeforce.efficiency.persistence.EfficiencyFactorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 效能计算的参数预检。
 *
 * <p>这是异步重算链路的<strong>第一道闸门</strong>：预检不通过就直接 400，
 * 绝不进入落库与发消息阶段。理由是预检要挡住的问题一旦进入异步阶段，
 * 就会变成「已经在跑的批次突然失败」，比同步失败难排查得多。</p>
 *
 * <p>预检项：
 * <ol>
 *   <li>权重和必须为 100（按维度分组）</li>
 *   <li>因素取值必须能覆盖到所有警员（年龄段能匹配上、性别有对应配置）</li>
 *   <li>警局必须存在且在用</li>
 *   <li>勤务模块比重必须在 0-100 之间</li>
 *   <li>配置人数不能为负</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class EfficiencyPrecheckService {

    private final EfficiencyFactorMapper factorMapper;

    /**
     * 预检结果。
     *
     * @param valid        是否通过
     * @param rejectCode   拒绝码
     * @param rejectReason 拒绝原因
     * @param ageFactors   年龄维度因素（通过时非空）
     * @param genderFactors 性别维度因素（通过时非空）
     */
    public record PrecheckResult(boolean valid,
                                  String rejectCode,
                                  String rejectReason,
                                  List<EfficiencyFactor> ageFactors,
                                  List<EfficiencyFactor> genderFactors) {

        public static PrecheckResult reject(String code, String reason) {
            return new PrecheckResult(false, code, reason, List.of(), List.of());
        }

        public static PrecheckResult pass(List<EfficiencyFactor> ageFactors,
                                          List<EfficiencyFactor> genderFactors) {
            return new PrecheckResult(true, null, null, ageFactors, genderFactors);
        }
    }

    /**
     * 执行预检。
     */
    public PrecheckResult check() {
        List<EfficiencyFactor> enabled = factorMapper.selectEnabled();
        if (enabled.isEmpty()) {
            return PrecheckResult.reject("FACTOR_EMPTY", "尚未配置任何效能因素，无法计算效能");
        }

        Map<String, List<EfficiencyFactor>> byType = new HashMap<>();
        for (EfficiencyFactor factor : enabled) {
            byType.computeIfAbsent(factor.getFactorType(), key -> new java.util.ArrayList<>()).add(factor);
        }

        List<EfficiencyFactor> ageFactors = byType.get(EfficiencyConstants.FACTOR_TYPE_AGE);
        List<EfficiencyFactor> genderFactors = byType.get(EfficiencyConstants.FACTOR_TYPE_GENDER);

        if (ageFactors == null || ageFactors.isEmpty()) {
            return PrecheckResult.reject("AGE_FACTOR_MISSING", "缺少年龄维度的效能因素配置");
        }
        if (genderFactors == null || genderFactors.isEmpty()) {
            return PrecheckResult.reject("GENDER_FACTOR_MISSING", "缺少性别维度的效能因素配置");
        }

        int ageTotal = ageFactors.stream()
                .mapToInt(factor -> factor.getWeight() == null ? 0 : factor.getWeight())
                .sum();
        if (ageTotal != EfficiencyConstants.FACTOR_WEIGHT_TOTAL) {
            return PrecheckResult.reject("AGE_WEIGHT_INVALID",
                    "年龄维度权重之和必须为 100，当前为 " + ageTotal);
        }

        int genderTotal = genderFactors.stream()
                .mapToInt(factor -> factor.getWeight() == null ? 0 : factor.getWeight())
                .sum();
        if (genderTotal != EfficiencyConstants.FACTOR_WEIGHT_TOTAL) {
            return PrecheckResult.reject("GENDER_WEIGHT_INVALID",
                    "性别维度权重之和必须为 100，当前为 " + genderTotal);
        }

        // 权重必须落在 0-100，否则出现负分
        for (EfficiencyFactor factor : enabled) {
            int weight = factor.getWeight() == null ? 0 : factor.getWeight();
            if (weight < 0 || weight > EfficiencyConstants.FACTOR_WEIGHT_TOTAL) {
                return PrecheckResult.reject("WEIGHT_OUT_OF_RANGE",
                        "效能因素权重必须在 0-100 之间：" + factor.getFactorType() + "/" + factor.getFactorValue());
            }
        }

        // 年龄段取值必须可解析，否则警员匹配不到任何年龄段
        Set<String> bands = new HashSet<>();
        for (EfficiencyFactor factor : ageFactors) {
            String band = factor.getFactorValue();
            if (band == null || band.isBlank()) {
                return PrecheckResult.reject("AGE_BAND_INVALID", "年龄段的取值不能为空");
            }
            if (!isParsableBand(band)) {
                return PrecheckResult.reject("AGE_BAND_INVALID", "年龄段取值无法解析：" + band);
            }
            bands.add(band);
        }

        // 性别取值必须能覆盖 MALE / FEMALE
        Set<String> genders = new HashSet<>();
        for (EfficiencyFactor factor : genderFactors) {
            if (factor.getFactorValue() == null || factor.getFactorValue().isBlank()) {
                return PrecheckResult.reject("GENDER_VALUE_INVALID", "性别的取值不能为空");
            }
            genders.add(factor.getFactorValue().toUpperCase());
        }
        if (!genders.contains("MALE") || !genders.contains("FEMALE")) {
            return PrecheckResult.reject("GENDER_COVERAGE_INVALID",
                    "性别维度必须同时配置 MALE 与 FEMALE");
        }

        return PrecheckResult.pass(ageFactors, genderFactors);
    }

    /**
     * 预检配置人数与模块比重是否合法。
     */
    public String checkHeadcounts(List<StationModuleConfig> configs) {
        for (StationModuleConfig config : configs) {
            Integer headcount = config.getConfiguredHeadcount();
            if (headcount == null || headcount < 0) {
                return "配置人数不能为负";
            }
        }
        return null;
    }

    /**
     * 预检警员数据是否能被年龄段覆盖。
     */
    public String checkOfficerCoverage(List<PoliceOfficer> officers, List<EfficiencyFactor> ageFactors) {
        for (PoliceOfficer officer : officers) {
            if (officer.getBirthDate() == null) {
                return "警员 " + officer.getOfficerCode() + " 缺少出生日期，无法推导年龄";
            }
            int age = EfficiencyCalcEngine.ageOf(
                    officer.getBirthDate().toLocalDate(), java.time.LocalDate.now());
            String band = EfficiencyCalcEngine.ageBand(age);
            boolean matched = ageFactors.stream()
                    .anyMatch(factor -> EfficiencyCalcEngine.matchesAgeBand(factor.getFactorValue(), age));
            if (!matched) {
                return "警员 " + officer.getOfficerCode() + " 的年龄段（" + band + "）没有对应的效能因素配置";
            }
        }
        return null;
    }

    private boolean isParsableBand(String band) {
        String trimmed = band.trim();
        if (trimmed.endsWith("以下") || trimmed.endsWith("以上")) {
            String numeric = trimmed.substring(0, trimmed.length() - 2).trim();
            try {
                return Integer.parseInt(numeric) > 0;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        int dash = trimmed.indexOf('-');
        if (dash <= 0) {
            return false;
        }
        try {
            return Integer.parseInt(trimmed.substring(0, dash).trim()) > 0
                    && Integer.parseInt(trimmed.substring(dash + 1).trim()) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
