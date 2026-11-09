package com.fragment.policeforce.efficiency.service;

import com.fragment.policeforce.entity.EfficiencyFactor;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.entity.StationModuleConfig;
import com.fragment.policeforce.efficiency.persistence.EfficiencyFactorMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EfficiencyPrecheckServiceTest {

    @Mock EfficiencyFactorMapper factorMapper;

    private EfficiencyPrecheckService service;

    @BeforeEach
    void setUp() {
        service = new EfficiencyPrecheckService(factorMapper);
    }

    @Test
    void emptyFactorTableShouldRejectRecalculation() {
        when(factorMapper.selectEnabled()).thenReturn(List.of());

        var result = service.check();

        assertThat(result.valid()).isFalse();
        assertThat(result.rejectCode()).isEqualTo("FACTOR_EMPTY");
    }

    @Test
    void ageWeightNotSummingTo100ShouldReject() {
        when(factorMapper.selectEnabled()).thenReturn(List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "25-35", 70, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "35-45", 20, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "MALE", 50, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "FEMALE", 50, 0)));

        var result = service.check();

        assertThat(result.valid()).isFalse();
        assertThat(result.rejectCode()).isEqualTo("AGE_WEIGHT_INVALID");
        assertThat(result.rejectReason()).contains("90");
    }

    @Test
    void genderWeightNotSummingTo100ShouldReject() {
        when(factorMapper.selectEnabled()).thenReturn(List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "25-35", 100, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "MALE", 40, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "FEMALE", 40, 0)));

        var result = service.check();

        assertThat(result.rejectCode()).isEqualTo("GENDER_WEIGHT_INVALID");
    }

    @Test
    void genderDimensionMissingFemaleShouldReject() {
        when(factorMapper.selectEnabled()).thenReturn(List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "25-35", 100, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "MALE", 100, 0)));

        var result = service.check();

        assertThat(result.rejectCode()).isEqualTo("GENDER_COVERAGE_INVALID");
    }

    @Test
    void unparsableAgeBandShouldReject() {
        when(factorMapper.selectEnabled()).thenReturn(List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "年轻", 100, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "MALE", 50, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "FEMALE", 50, 0)));

        var result = service.check();

        assertThat(result.rejectCode()).isEqualTo("AGE_BAND_INVALID");
    }

    @Test
    void validConfigurationShouldPass() {
        when(factorMapper.selectEnabled()).thenReturn(List.of(
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "25以下", 20, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "25-35", 40, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_AGE, "35-45", 40, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "MALE", 60, 0),
                factor(EfficiencyConstants.FACTOR_TYPE_GENDER, "FEMALE", 40, 0)));

        var result = service.check();

        assertThat(result.valid()).isTrue();
        assertThat(result.ageFactors()).hasSize(3);
        assertThat(result.genderFactors()).hasSize(2);
    }

    @Test
    void negativeConfiguredHeadcountShouldReject() {
        StationModuleConfig config = new StationModuleConfig();
        config.setConfiguredHeadcount(-1);

        assertThat(service.checkHeadcounts(List.of(config))).isEqualTo("配置人数不能为负");
    }

    @Test
    void officerWithoutBirthDateShouldRejectCoverage() {
        PoliceOfficer officer = new PoliceOfficer();
        officer.setOfficerCode("P001");

        String error = service.checkOfficerCoverage(
                List.of(officer), List.of(factor(EfficiencyConstants.FACTOR_TYPE_AGE, "25-35", 100, 0)));

        assertThat(error).contains("P001");
    }

    @Test
    void officerOutsideConfiguredAgeBandsShouldRejectCoverage() {
        PoliceOfficer officer = new PoliceOfficer();
        officer.setOfficerCode("P002");
        officer.setBirthDate(LocalDate.of(1990, 1, 1).atStartOfDay());

        String error = service.checkOfficerCoverage(
                List.of(officer), List.of(factor(EfficiencyConstants.FACTOR_TYPE_AGE, "25-35", 100, 0)));

        assertThat(error).contains("P002");
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
