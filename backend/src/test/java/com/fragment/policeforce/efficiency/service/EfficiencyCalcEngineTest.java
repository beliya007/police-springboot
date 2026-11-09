package com.fragment.policeforce.efficiency.service;

import com.fragment.policeforce.entity.EfficiencyFactor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 效能计算引擎的单元测试。
 *
 * <p>引擎是纯函数，所以可以直接断言精确数值 —— 这也是把算法从 IO 中剥离出来的价值。</p>
 */
class EfficiencyCalcEngineTest {

    @Test
    void officerScoreShouldMultiplyBaseScoreByAgeAndGenderWeight() {
        List<EfficiencyFactor> ageFactors = List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "25-35", 80, 0));
        List<EfficiencyFactor> genderFactors = List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "MALE", 100, 0));

        double score = EfficiencyCalcEngine.officerScore(30, "MALE", ageFactors, genderFactors, null);

        // 100 * 0.8 * 1.0 = 80
        assertThat(score).isEqualTo(80.0d);
    }

    @Test
    void officerScoreShouldApplyBothDimensionsIndependently() {
        List<EfficiencyFactor> ageFactors = List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "25-35", 50, 0));
        List<EfficiencyFactor> genderFactors = List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "FEMALE", 90, 0));

        double score = EfficiencyCalcEngine.officerScore(28, "FEMALE", ageFactors, genderFactors, null);

        // 100 * 0.5 * 0.9 = 45
        assertThat(score).isEqualTo(45.0d);
    }

    @Test
    void unmatchedFactorsShouldFallBackToFullScore() {
        List<EfficiencyFactor> ageFactors = List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "25-35", 60, 0));
        List<EfficiencyFactor> genderFactors = List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "FEMALE", 60, 0));

        // 年龄 60 落在所有配置年龄段之外、性别也不匹配，两个维度都按 1.0 计算
        double score = EfficiencyCalcEngine.officerScore(60, "MALE", ageFactors, genderFactors, null);

        // 未匹配到任何配置时按 1.0 计算，避免出现 0 分导致看板塌陷
        assertThat(score).isEqualTo(100.0d);
    }

    @Test
    void injuredConclusionShouldApplyLargestPenaltyOnlyOnce() {
        List<EfficiencyFactor> ageFactors = List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "25-35", 100, 30));
        List<EfficiencyFactor> genderFactors = List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "MALE", 100, 10));

        double score = EfficiencyCalcEngine.officerScore(30, "MALE", ageFactors, genderFactors,
                EfficiencyConstants.CONCLUSION_INJURED);

        // 取最大下调 30%，而不是 30% + 10% 叠加
        assertThat(score).isEqualTo(70.0d);
    }

    @Test
    void failedConclusionShouldNotBeCountedAsEffectiveHeadcount() {
        assertThat(EfficiencyCalcEngine.countsAsEffectiveHeadcount(EfficiencyConstants.CONCLUSION_FAIL))
                .isFalse();
        assertThat(EfficiencyCalcEngine.countsAsEffectiveHeadcount(EfficiencyConstants.CONCLUSION_PASS))
                .isTrue();
        assertThat(EfficiencyCalcEngine.countsAsEffectiveHeadcount(null)).isTrue();
    }

    @Test
    void ageBandShouldFollowConfiguredBoundaries() {
        assertThat(EfficiencyCalcEngine.ageBand(20)).isEqualTo("25以下");
        assertThat(EfficiencyCalcEngine.ageBand(25)).isEqualTo("25-35");
        assertThat(EfficiencyCalcEngine.ageBand(35)).isEqualTo("35-45");
        assertThat(EfficiencyCalcEngine.ageBand(45)).isEqualTo("45-55");
        assertThat(EfficiencyCalcEngine.ageBand(60)).isEqualTo("55以上");
    }

    @Test
    void ageOfShouldDeriveFullYearsFromBirthDate() {
        LocalDate reference = LocalDate.of(2026, 1, 1);
        assertThat(EfficiencyCalcEngine.ageOf(LocalDate.of(1990, 6, 15), reference)).isEqualTo(35);
        assertThat(EfficiencyCalcEngine.ageOf(LocalDate.of(1990, 12, 31), reference)).isEqualTo(35);
        assertThat(EfficiencyCalcEngine.ageOf(null, reference)).isZero();
    }

    @Test
    void moduleScoreShouldMultiplyHeadcountRatioAndAverageOfficerScore() {
        assertThat(EfficiencyCalcEngine.moduleScore(100, 30, 80.0d)).isEqualTo(2400.0d);
    }

    @Test
    void actualEffectivenessShouldGuardAgainstZeroConfiguredHeadcount() {
        assertThat(EfficiencyCalcEngine.actualEffectiveness(80, 100)).isEqualTo(0.8d);
        assertThat(EfficiencyCalcEngine.actualEffectiveness(5, 0)).isEqualTo(1.0d);
    }

    @Test
    void shortageShouldBeConfiguredMinusEffective() {
        assertThat(EfficiencyCalcEngine.shortageHeadcount(100, 80)).isEqualTo(20);
        assertThat(EfficiencyCalcEngine.shortageHeadcount(80, 100)).isEqualTo(-20);
    }

    private EfficiencyFactor factor(String type, String value, int weight, int injuryPenalty) {
        EfficiencyFactor factor = new EfficiencyFactor();
        factor.setFactorType(type);
        factor.setFactorValue(value);
        factor.setWeight(weight);
        factor.setInjuryPenalty(injuryPenalty);
        factor.setStatus("ENABLED");
        return factor;
    }
}
