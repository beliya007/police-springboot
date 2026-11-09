package com.fragment.policeforce.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.admin.dto.DutyModuleAddDTO;
import com.fragment.policeforce.admin.dto.DutyModuleUpdateDTO;
import com.fragment.policeforce.admin.dto.ModuleRatioUpdateDTO;
import com.fragment.policeforce.common.audit.AdminAuditHelper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.DutyModule;
import com.fragment.policeforce.entity.StationModuleConfig;
import com.fragment.policeforce.efficiency.service.EfficiencyConstants;
import com.fragment.policeforce.efficiency.service.RecalculationService;
import com.fragment.policeforce.mapper.DutyModuleMapper;
import com.fragment.policeforce.mapper.StationModuleConfigMapper;
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
 * 勤务模块管理服务 —— 对应 read.txt 中
 * 「后台管理系统中各个模块的增删改查及比重设置」。
 *
 * <p>比重调整会立刻影响效能计算，所以每次改动后都要触发一次全量重算。
 * 重算是异步的（202 Accepted），接口不会因为警局多而变慢。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DutyModuleAdminService {

    private final DutyModuleMapper moduleMapper;
    private final StationModuleConfigMapper configMapper;
    private final RecalculationService recalculationService;
    private final AdminAuditHelper adminAuditHelper;

    /**
     * 列出全部模块（含停用的）。
     */
    public List<DutyModule> listAll() {
        return moduleMapper.selectList(new LambdaQueryWrapper<DutyModule>()
                .orderByAsc(DutyModule::getId));
    }

    /**
     * 新增模块。
     */
    @Transactional(rollbackFor = Exception.class)
    public DutyModule add(DutyModuleAddDTO dto) {
        DutyModule module = new DutyModule();
        adminAuditHelper.execute(
                "DUTY_MODULE",
                "ADD",
                "DUTY_MODULE",
                module::getId,
                () -> "moduleCode=" + dto.getModuleCode() + ", moduleName=" + dto.getModuleName(),
                () -> {
                    String moduleCode = dto.getModuleCode().trim();
                    validateCodeUnique(moduleCode, null);
                    module.setModuleCode(moduleCode);
                    module.setModuleName(dto.getModuleName().trim());
                    module.setRatio(dto.getRatio() == null ? 0 : dto.getRatio());
                    module.setPhysicalRequired(dto.getPhysicalRequired() == null ? 0 : dto.getPhysicalRequired());
                    module.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "ENABLED");
                    module.setCreatedAt(LocalDateTime.now());
                    module.setUpdatedAt(LocalDateTime.now());
                    if (moduleMapper.insert(module) <= 0) {
                        throw new BusinessException("新增勤务模块失败");
                    }
                }
        );
        return module;
    }

    /**
     * 更新模块（含比重）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(DutyModuleUpdateDTO dto) {
        DutyModule existing = moduleMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BusinessException("勤务模块不存在");
        }
        adminAuditHelper.execute(
                "DUTY_MODULE",
                "UPDATE",
                "DUTY_MODULE",
                existing::getId,
                () -> "moduleCode=" + existing.getModuleCode() + ", ratio=" + existing.getRatio(),
                () -> {
                    String moduleCode = dto.getModuleCode().trim();
                    validateCodeUnique(moduleCode, existing.getId());
                    existing.setModuleCode(moduleCode);
                    existing.setModuleName(dto.getModuleName().trim());
                    existing.setRatio(dto.getRatio() == null ? 0 : dto.getRatio());
                    existing.setPhysicalRequired(dto.getPhysicalRequired() == null ? 0 : dto.getPhysicalRequired());
                    if (StringUtils.hasText(dto.getStatus())) {
                        existing.setStatus(dto.getStatus());
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    if (moduleMapper.updateById(existing) <= 0) {
                        throw new BusinessException("更新勤务模块失败");
                    }
                }
        );
        // 比重或属性变了 → 触发全量重算。
        triggerRecalculation(EfficiencyConstants.TRIGGER_MODULE_RATIO);
    }

    /**
     * 删除模块。
     *
     * <p>模块已经被警局引用时不允许删除，否则配置人数会变成孤儿数据。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        DutyModule existing = moduleMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("勤务模块不存在");
        }
        adminAuditHelper.execute(
                "DUTY_MODULE",
                "DELETE",
                "DUTY_MODULE",
                () -> id,
                () -> "moduleCode=" + existing.getModuleCode() + ", moduleName=" + existing.getModuleName(),
                () -> {
                    long configCount = configMapper.selectCount(
                            new LambdaQueryWrapper<StationModuleConfig>()
                                    .eq(StationModuleConfig::getModuleId, id));
                    if (configCount > 0) {
                        throw new BusinessException("当前模块已被警局配置引用，不能删除");
                    }
                    if (moduleMapper.deleteById(id) <= 0) {
                        throw new BusinessException("删除勤务模块失败");
                    }
                }
        );
    }

    /**
     * 批量调整模块比重。
     *
     * <p>同步校验比重之和不超过 100；调整后触发全量重算（异步）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRatios(ModuleRatioUpdateDTO dto) {
        adminAuditHelper.execute(
                "DUTY_MODULE",
                "UPDATE_RATIO",
                "DUTY_MODULE",
                () -> null,
                () -> "items=" + dto.getItems().size(),
                () -> {
                    Set<Long> moduleIds = new HashSet<>();
                    int total = 0;
                    for (ModuleRatioUpdateDTO.Item item : dto.getItems()) {
                        if (!moduleIds.add(item.getModuleId())) {
                            throw new BusinessException(400, "比重配置中存在重复的模块");
                        }
                        DutyModule module = moduleMapper.selectById(item.getModuleId());
                        if (module == null) {
                            throw new BusinessException(400, "勤务模块不存在：" + item.getModuleId());
                        }
                        int ratio = item.getRatio() == null ? 0 : item.getRatio();
                        if (ratio < 0 || ratio > 100) {
                            throw new BusinessException(400, "模块比重必须在 0-100 之间");
                        }
                        module.setRatio(ratio);
                        module.setUpdatedAt(LocalDateTime.now());
                        if (moduleMapper.updateById(module) <= 0) {
                            throw new BusinessException("更新模块比重失败");
                        }
                        total += ratio;
                    }
                    if (total > 100) {
                        throw new BusinessException(400, "模块比重之和不能超过 100，当前为 " + total);
                    }
                }
        );
        triggerRecalculation(EfficiencyConstants.TRIGGER_MODULE_RATIO);
    }

    /**
     * 触发全量重算（异步）。失败不影响本次配置修改的提交。
     */
    private void triggerRecalculation(String triggerSource) {
        try {
            recalculationService.trigger(triggerSource, EfficiencyConstants.SCOPE_ALL, null, null, null);
        } catch (RuntimeException exception) {
            log.warn("Failed to trigger recalculation after module change. triggerSource={}, reason={}",
                    triggerSource, exception.getMessage());
        }
    }

    private void validateCodeUnique(String moduleCode, Long excludeId) {
        LambdaQueryWrapper<DutyModule> wrapper = new LambdaQueryWrapper<DutyModule>()
                .eq(DutyModule::getModuleCode, moduleCode);
        if (excludeId != null) {
            wrapper.ne(DutyModule::getId, excludeId);
        }
        if (moduleMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("模块编码已存在");
        }
    }
}
