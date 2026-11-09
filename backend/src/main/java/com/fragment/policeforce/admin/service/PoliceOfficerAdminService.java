package com.fragment.policeforce.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fragment.policeforce.admin.dto.PoliceOfficerAddDTO;
import com.fragment.policeforce.admin.dto.PoliceOfficerUpdateDTO;
import com.fragment.policeforce.common.audit.AdminAuditHelper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.DutyModule;
import com.fragment.policeforce.entity.PoliceOfficer;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.efficiency.service.EfficiencyCalcEngine;
import com.fragment.policeforce.efficiency.service.EfficiencyConstants;
import com.fragment.policeforce.efficiency.service.RecalculationService;
import com.fragment.policeforce.mapper.DutyModuleMapper;
import com.fragment.policeforce.mapper.PoliceOfficerMapper;
import com.fragment.policeforce.mapper.PoliceStationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 警员信息管理服务 —— 对应 read.txt 中
 * 「后台管理系统中各警局的人员信息的管理」。
 *
 * <p>警员的性别与出生日期是效能计算的两类因素输入，
 * 所以人员信息发生实质变化（新增 / 删除 / 调岗 / 状态变化）时，
 * 都要触发所属警局的增量重算。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoliceOfficerAdminService {

    private final PoliceOfficerMapper officerMapper;
    private final PoliceStationMapper stationMapper;
    private final DutyModuleMapper moduleMapper;
    private final RecalculationService recalculationService;
    private final AdminAuditHelper adminAuditHelper;

    /**
     * 列出某警局的警员。
     */
    public List<PoliceOfficer> listByStation(Long stationId) {
        return officerMapper.selectList(new LambdaQueryWrapper<PoliceOfficer>()
                .eq(stationId != null, PoliceOfficer::getStationId, stationId)
                .orderByAsc(PoliceOfficer::getOfficerCode));
    }

    /**
     * 分页查询警员。
     */
    public Page<PoliceOfficer> page(Long stationId, Long moduleId, String keyword, String status,
                                    long pageNum, long pageSize) {
        LambdaQueryWrapper<PoliceOfficer> wrapper = new LambdaQueryWrapper<PoliceOfficer>()
                .eq(stationId != null, PoliceOfficer::getStationId, stationId)
                .eq(moduleId != null, PoliceOfficer::getModuleId, moduleId)
                .eq(StringUtils.hasText(status), PoliceOfficer::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(PoliceOfficer::getOfficerCode, keyword)
                        .or()
                        .like(PoliceOfficer::getName, keyword))
                .orderByAsc(PoliceOfficer::getOfficerCode);

        return officerMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 新增警员。
     */
    @Transactional(rollbackFor = Exception.class)
    public PoliceOfficer add(PoliceOfficerAddDTO dto) {
        validateGender(dto.getGender());
        validateStationAndModule(dto.getStationId(), dto.getModuleId());
        validateCodeUnique(dto.getOfficerCode().trim(), null);
        validateUserIdUnique(dto.getUserId(), null);

        PoliceOfficer officer = new PoliceOfficer();
        officer.setOfficerCode(dto.getOfficerCode().trim());
        officer.setName(dto.getName().trim());
        officer.setUserId(dto.getUserId());
        officer.setStationId(dto.getStationId());
        officer.setModuleId(dto.getModuleId());
        officer.setGender(dto.getGender().trim().toUpperCase());
        officer.setBirthDate(dto.getBirthDate());
        officer.setRankCode(StringUtils.hasText(dto.getRankCode()) ? dto.getRankCode().trim() : null);
        officer.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "ACTIVE");
        officer.setCreatedAt(LocalDateTime.now());
        officer.setUpdatedAt(LocalDateTime.now());

        adminAuditHelper.execute(
                "POLICE_OFFICER",
                "ADD",
                "POLICE_OFFICER",
                officer::getId,
                () -> "officerCode=" + officer.getOfficerCode() + ", name=" + officer.getName(),
                () -> {
                    if (officerMapper.insert(officer) <= 0) {
                        throw new BusinessException("新增警员失败");
                    }
                }
        );
        triggerRecalculation(officer.getStationId());
        return officer;
    }

    /**
     * 更新警员。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(PoliceOfficerUpdateDTO dto) {
        PoliceOfficer existing = officerMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException("警员不存在");
        }
        validateGender(dto.getGender());
        validateStationAndModule(dto.getStationId(), dto.getModuleId());
        validateCodeUnique(dto.getOfficerCode().trim(), existing.getId());
        validateUserIdUnique(dto.getUserId(), existing.getId());

        Long previousStationId = existing.getStationId();

        adminAuditHelper.execute(
                "POLICE_OFFICER",
                "UPDATE",
                "POLICE_OFFICER",
                existing::getId,
                () -> "officerCode=" + existing.getOfficerCode() + ", stationId=" + existing.getStationId(),
                () -> {
                    existing.setOfficerCode(dto.getOfficerCode().trim());
                    existing.setName(dto.getName().trim());
                    existing.setUserId(dto.getUserId());
                    existing.setStationId(dto.getStationId());
                    existing.setModuleId(dto.getModuleId());
                    existing.setGender(dto.getGender().trim().toUpperCase());
                    existing.setBirthDate(dto.getBirthDate());
                    existing.setRankCode(StringUtils.hasText(dto.getRankCode()) ? dto.getRankCode().trim() : null);
                    if (StringUtils.hasText(dto.getStatus())) {
                        existing.setStatus(dto.getStatus());
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    if (officerMapper.updateById(existing) <= 0) {
                        throw new BusinessException("更新警员失败");
                    }
                }
        );

        // 调岗了就要重算新旧两个警局。
        triggerRecalculation(dto.getStationId());
        if (previousStationId != null && !previousStationId.equals(dto.getStationId())) {
            triggerRecalculation(previousStationId);
        }
    }

    /**
     * 删除警员。
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        PoliceOfficer existing = officerMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("警员不存在");
        }
        adminAuditHelper.execute(
                "POLICE_OFFICER",
                "DELETE",
                "POLICE_OFFICER",
                () -> id,
                () -> "officerCode=" + existing.getOfficerCode() + ", name=" + existing.getName(),
                () -> {
                    if (officerMapper.deleteById(id) <= 0) {
                        throw new BusinessException("删除警员失败");
                    }
                }
        );
        triggerRecalculation(existing.getStationId());
    }

    /**
     * 触发警局的增量重算（异步）。失败不影响本次人员变更的提交。
     */
    private void triggerRecalculation(Long stationId) {
        if (stationId == null) {
            return;
        }
        try {
            recalculationService.trigger(
                    EfficiencyConstants.TRIGGER_HEADCOUNT,
                    EfficiencyConstants.SCOPE_STATION,
                    stationId,
                    null,
                    null);
        } catch (RuntimeException exception) {
            log.warn("Failed to trigger recalculation after officer change. stationId={}, reason={}",
                    stationId, exception.getMessage());
        }
    }

    private void validateGender(String gender) {
        if (!StringUtils.hasText(gender)) {
            throw new BusinessException(400, "性别不能为空");
        }
        String normalized = gender.trim().toUpperCase();
        if (!"MALE".equals(normalized) && !"FEMALE".equals(normalized)) {
            throw new BusinessException(400, "性别只能是 MALE 或 FEMALE");
        }
    }

    private void validateStationAndModule(Long stationId, Long moduleId) {
        PoliceStation station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(400, "所属警局不存在");
        }
        DutyModule module = moduleMapper.selectById(moduleId);
        if (module == null) {
            throw new BusinessException(400, "所属勤务模块不存在");
        }
    }

    private void validateCodeUnique(String officerCode, Long excludeId) {
        LambdaQueryWrapper<PoliceOfficer> wrapper = new LambdaQueryWrapper<PoliceOfficer>()
                .eq(PoliceOfficer::getOfficerCode, officerCode);
        if (excludeId != null) {
            wrapper.ne(PoliceOfficer::getId, excludeId);
        }
        if (officerMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("警员编号已存在");
        }
    }

    private void validateUserIdUnique(Long userId, Long excludeId) {
        if (userId == null) {
            return;
        }
        LambdaQueryWrapper<PoliceOfficer> wrapper = new LambdaQueryWrapper<PoliceOfficer>()
                .eq(PoliceOfficer::getUserId, userId);
        if (excludeId != null) {
            wrapper.ne(PoliceOfficer::getId, excludeId);
        }
        if (officerMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("该登录账号已绑定其他警员");
        }
    }

    /**
     * 供外部计算警员年龄的便捷方法。
     */
    public int ageOf(PoliceOfficer officer) {
        return EfficiencyCalcEngine.ageOf(
                officer.getBirthDate() == null ? null : officer.getBirthDate().toLocalDate(),
                java.time.LocalDate.now());
    }
}
