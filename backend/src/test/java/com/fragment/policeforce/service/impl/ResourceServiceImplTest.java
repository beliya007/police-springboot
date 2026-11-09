package com.fragment.policeforce.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fragment.policeforce.common.audit.AdminAuditHelper;
import com.fragment.policeforce.common.redis.StationCatalogCache;
import com.fragment.policeforce.dto.PoliceStationQueryDTO;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.mapper.PoliceStationMapper;
import com.fragment.policeforce.service.DutySlotService;
import com.fragment.policeforce.service.SysDictDataService;
import com.fragment.policeforce.vo.PoliceStationVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceImplTest {

    @Mock private PoliceStationMapper policeStationMapper;
    @Mock private SysDictDataService sysDictDataService;
    @Mock private DutySlotService dutySlotService;
    @Mock private AllocationMapper reservationMapper;
    @Mock private StationCatalogCache resourceCatalogCache;
    @Mock private AdminAuditHelper adminAuditHelper;

    private PoliceStationServiceImpl policeStationService;

    @BeforeEach
    void setUp() {
        initTableInfo(PoliceStation.class);
        policeStationService = new PoliceStationServiceImpl();
        ReflectionTestUtils.setField(policeStationService, "baseMapper", policeStationMapper);
        ReflectionTestUtils.setField(policeStationService, "sysDictDataService", sysDictDataService);
        ReflectionTestUtils.setField(policeStationService, "dutySlotService", dutySlotService);
        ReflectionTestUtils.setField(policeStationService, "reservationMapper", reservationMapper);
        ReflectionTestUtils.setField(policeStationService, "resourceCatalogCache", resourceCatalogCache);
        ReflectionTestUtils.setField(policeStationService, "adminAuditHelper", adminAuditHelper);
    }

    @Test
    void unfilteredSearchUsesOnlyTheHomeCatalogCache() {
        PoliceStationVO cached = new PoliceStationVO();
        cached.setId(1L);
        cached.setStationName("城东巡防站");
        when(resourceCatalogCache.getHomeStationList(any())).thenReturn(List.of(cached));

        List<PoliceStationVO> result = policeStationService.search(new PoliceStationQueryDTO());

        assertThat(result).containsExactly(cached);
        verify(resourceCatalogCache).getHomeStationList(any(Supplier.class));
        verifyNoInteractions(policeStationMapper, sysDictDataService);
    }

    @Test
    void filteredSearchBypassesTheHomeCatalogCache() {
        PoliceStationQueryDTO query = new PoliceStationQueryDTO();
        query.setName("巡防");
        PoliceStation station = new PoliceStation();
        station.setId(2L);
        station.setStationCode("RM-PATROL");
        station.setStationName("城东巡防站");
        station.setStationType("FIELD");
        station.setStatus("AVAILABLE");
        when(policeStationMapper.selectList(any())).thenReturn(List.of(station));
        when(sysDictDataService.getDictLabelMap("station_type")).thenReturn(Map.of("FIELD", "场地"));
        when(sysDictDataService.getDictLabelMap("resource_status")).thenReturn(Map.of("AVAILABLE", "可预约"));

        List<PoliceStationVO> result = policeStationService.search(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStationTypeDesc()).isEqualTo("场地");
        verify(policeStationMapper).selectList(any());
        verify(resourceCatalogCache, never()).getHomeStationList(any());
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
