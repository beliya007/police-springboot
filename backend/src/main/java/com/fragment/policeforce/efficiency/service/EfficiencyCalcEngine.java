package com.fragment.policeforce.efficiency.service;

import com.fragment.policeforce.entity.EfficiencyFactor;
import com.fragment.policeforce.entity.ExamResult;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;

/**
 * 效能计算引擎 —— 纯函数，无 IO、无状态。
 *
 * <p>把计算逻辑做成纯函数是这个项目可测试性的关键：算法可以脱离数据库与 MQ 单独单测，
 * 也能通过「同一批输入必然得到同一批输出」证明计算是<strong>可重放</strong>的
 * （重放历史版本用于对比调整前后的效能变化）。</p>
 *
 * <pre>
 * 个体效能分 officerScore = baseScore
 *                          × Σ(匹配该警员的因素权重 / 100)
 *                          × 体检调整系数
 *
 * 模块效能 moduleScore(station, module)
 *                        = configuredHeadcount × module.ratio/100 × avg(officerScore)
 *
 * 警局效能 stationEfficiency = Σ moduleScore
 *
 * 有效警力 effectiveHeadcount = 体检合格(PASS 或未体检)且在编的人数
 *                             （体检结论 FAIL 的警员不计入）
 * </pre>
 */
public final class EfficiencyCalcEngine {

    private EfficiencyCalcEngine() {
    }

    /**
     * 计算单个警员的效能分。
     *
     * <p>因素权重按「匹配到的年龄段权重/100」与「匹配到的性别权重/100」相乘，
     * 两个维度各自独立归一化。体检伤病系数按因素配置里的 injury_penalty
     * 取最大下调比例，避免同一名警员被重复打折。</p>
     *
     * @param age        年龄（由出生日期推导）
     * @param gender     性别
     * @param ageFactors  年龄维度的因素配置
     * @param genderFactors 性别维度的因素配置
     * @param conclusion 体检结论，null 表示未体检
     * @return 个体效能分
     */
    public static double officerScore(int age,
                                      String gender,
                                      List<EfficiencyFactor> ageFactors,
                                      List<EfficiencyFactor> genderFactors,
                                      String conclusion) {
        double ageFactor = 1.0d;
        int agePenalty = 0;
        for (EfficiencyFactor factor : ageFactors) {
            if (matchesAgeBand(factor.getFactorValue(), age)) {
                ageFactor = factor.getWeight() / (double) EfficiencyConstants.FACTOR_WEIGHT_TOTAL;
                agePenalty = factor.getInjuryPenalty() == null ? 0 : factor.getInjuryPenalty();
                break;
            }
        }

        double genderFactor = 1.0d;
        int genderPenalty = 0;
        for (EfficiencyFactor factor : genderFactors) {
            if (factor.getFactorValue() != null && factor.getFactorValue().equalsIgnoreCase(gender)) {
                genderFactor = factor.getWeight() / (double) EfficiencyConstants.FACTOR_WEIGHT_TOTAL;
                genderPenalty = factor.getInjuryPenalty() == null ? 0 : factor.getInjuryPenalty();
                break;
            }
        }

        double score = EfficiencyConstants.BASE_SCORE * ageFactor * genderFactor;

        if (EfficiencyConstants.CONCLUSION_INJURED.equals(conclusion)) {
            // 伤病按最大下调比例打折一次，而不是把两个维度的惩罚叠加。
            int penalty = Math.max(agePenalty, genderPenalty);
            score = score * (1.0d - Math.min(penalty, 100) / 100.0d);
        }
        // FAIL 不参与计算：调用方应在统计有效警力时直接排除 FAIL 的警员。
        return round(score);
    }

    /**
     * 体检 FAIL 的警员不计入有效警力。
     */
    public static boolean countsAsEffectiveHeadcount(String conclusion) {
        return !EfficiencyConstants.CONCLUSION_FAIL.equals(conclusion);
    }

    /**
     * 推导年龄段标签，与 {@code efficiency_factor.factor_value} 的取值对应。
     *
     * <p>年龄段定义：25以下 / 25-35 / 35-45 / 45-55 / 55以上。</p>
     */
    public static String ageBand(int age) {
        if (age < 25) {
            return "25以下";
        }
        if (age < 35) {
            return "25-35";
        }
        if (age < 45) {
            return "35-45";
        }
        if (age < 55) {
            return "45-55";
        }
        return "55以上";
    }

    /**
     * 由出生日期推导年龄。
     */
    public static int ageOf(LocalDate birthDate, LocalDate reference) {
        if (birthDate == null) {
            return 0;
        }
        return Period.between(birthDate, reference).getYears();
    }

    /**
     * 判断某个年龄段配置是否命中给定年龄。
     *
     * <p>配置值形如 {@code 25-35}、{@code 25以下}、{@code 55以上}。
     * 解析失败时保守地不命中，避免脏配置把所有人算成同一档。</p>
     */
    static boolean matchesAgeBand(String band, int age) {
        if (band == null || band.isBlank()) {
            return false;
        }
        String trimmed = band.trim();
        if (trimmed.endsWith("以下")) {
            int upper = parseInt(trimmed.replace("以下", ""));
            return upper > 0 && age < upper;
        }
        if (trimmed.endsWith("以上")) {
            int lower = parseInt(trimmed.replace("以上", ""));
            return lower > 0 && age >= lower;
        }
        int dash = trimmed.indexOf('-');
        if (dash <= 0) {
            return false;
        }
        int lower = parseInt(trimmed.substring(0, dash));
        int upper = parseInt(trimmed.substring(dash + 1));
        if (lower <= 0 || upper <= 0) {
            return false;
        }
        // 左闭右闭，与「25-35」的自然读法一致
        return age >= lower && age <= upper;
    }

    /**
     * 模块效能分 = 配置人数 × 模块比重 × 平均个体效能分。
     *
     * @param configuredHeadcount 核定配置人数
     * @param moduleRatio          模块比重（百分比 0-100）
     * @param averageOfficerScore  模块内平均个体效能分
     */
    public static double moduleScore(int configuredHeadcount, int moduleRatio, double averageOfficerScore) {
        return round(configuredHeadcount * (moduleRatio / 100.0d) * averageOfficerScore);
    }

    /**
     * 实际效能 = 有效警力 / 配置人数。
     *
     * <p>配置人数为 0 时返回 1.0（视为满编），避免除零产生 NaN 污染看板。</p>
     */
    public static double actualEffectiveness(int effectiveHeadcount, int configuredHeadcount) {
        if (configuredHeadcount <= 0) {
            return 1.0d;
        }
        return round(effectiveHeadcount / (double) configuredHeadcount);
    }

    /**
     * 缺编人数 = 配置人数 - 有效警力。负数表示超编。
     */
    public static int shortageHeadcount(int configuredHeadcount, int effectiveHeadcount) {
        return configuredHeadcount - effectiveHeadcount;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /**
     * 保留两位小数，让看板展示与单测断言都稳定。
     */
    public static double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    /**
     * 供体检结论覆盖表使用的索引结构。
     */
    public interface ConclusionLookup {
        /**
         * 返回警员的最新体检结论，没有则返回 null。
         */
        String conclusionOf(Long officerId);
    }

    /**
     * 便捷方法：从 Map 形式的结论索引中取结论。
     */
    public static ConclusionLookup lookupOf(Map<Long, ExamResult> results) {
        return officerId -> {
            ExamResult result = results.get(officerId);
            return result == null ? null : result.getConclusion();
        };
    }
}
