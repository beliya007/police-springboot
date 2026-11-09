package com.fragment.policeforce.allocation.support;

import com.fragment.policeforce.allocation.reminder.AllocationAutoCancelService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.allocation.support.AllocationNoGenerator;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationPersistenceHelperTest {

    @BeforeEach
    void setUp() {
        if (TableInfoHelper.getTableInfo(AllocationAssignment.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(AllocationAssignment.class.getName());
        TableInfoHelper.initTableInfo(assistant, AllocationAssignment.class);
    }

    @Test
    void saveWithRetryShouldGenerateNewNumberWhenNoActiveAllocationExists() {
        AllocationMapper mapper = mock(AllocationMapper.class);
        AllocationNoGenerator numberGenerator = mock(AllocationNoGenerator.class);
        AllocationPersistenceHelper helper = helper(mapper, numberGenerator);
        AllocationAssignment allocation = allocation();
        when(numberGenerator.nextAllocationNo()).thenReturn("R-1", "R-2");
        when(mapper.insertIgnore(allocation)).thenReturn(0, 1);
        when(mapper.selectOne(any())).thenReturn(null);

        helper.saveWithRetry(allocation);

        assertThat(allocation.getAllocationNo()).isEqualTo("R-2");
        verify(mapper, times(2)).insertIgnore(allocation);
    }

    @Test
    void saveWithRetryShouldRejectWhenActiveAllocationExists() {
        AllocationMapper mapper = mock(AllocationMapper.class);
        AllocationNoGenerator numberGenerator = mock(AllocationNoGenerator.class);
        AllocationPersistenceHelper helper = helper(mapper, numberGenerator);
        AllocationAssignment allocation = allocation();
        when(numberGenerator.nextAllocationNo()).thenReturn("R-1");
        when(mapper.insertIgnore(allocation)).thenReturn(0);
        when(mapper.selectOne(any())).thenReturn(existingReservation());

        assertThatThrownBy(() -> helper.saveWithRetry(allocation))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该警员已被调配到该时段，不能重复派出");

        verify(mapper).insertIgnore(allocation);
    }

    private AllocationPersistenceHelper helper(AllocationMapper mapper,
                                                AllocationNoGenerator numberGenerator) {
        return new AllocationPersistenceHelper(
                numberGenerator,
                mapper,
                mock(AllocationAutoCancelService.class)
        );
    }

    private AllocationAssignment allocation() {
        AllocationAssignment allocation = new AllocationAssignment();
        allocation.setUserId(7L);
        allocation.setSlotId(10L);
        allocation.setIsActive(1);
        return allocation;
    }

    private AllocationAssignment existingReservation() {
        AllocationAssignment allocation = allocation();
        allocation.setId(99L);
        return allocation;
    }
}
