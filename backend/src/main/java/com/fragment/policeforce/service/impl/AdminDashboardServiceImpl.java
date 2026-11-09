package com.fragment.policeforce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fragment.policeforce.allocation.model.AllocationStatusConstants;
import com.fragment.policeforce.common.constants.DutySlotStatusConstants;
import com.fragment.policeforce.common.constants.DutySlotTypeConstants;
import com.fragment.policeforce.entity.MessageOutbox;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.model.AllocationReminderTask;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.entity.DutySlot;
import com.fragment.policeforce.entity.UserNotification;
import com.fragment.policeforce.mapper.MessageOutboxMapper;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.allocation.persistence.AllocationReminderTaskMapper;
import com.fragment.policeforce.mapper.PoliceStationMapper;
import com.fragment.policeforce.mapper.DutySlotMapper;
import com.fragment.policeforce.mapper.UserNotificationMapper;
import com.fragment.policeforce.service.AdminDashboardService;
import com.fragment.policeforce.vo.AdminDashboardHotSlotVO;
import com.fragment.policeforce.vo.AdminDashboardStationHeatVO;
import com.fragment.policeforce.vo.AdminDashboardVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String ADMIN_AUDIT_LOG_EVENT_TYPE = "ADMIN_AUDIT_LOG";
    private static final String RESOURCE_AVAILABLE = "AVAILABLE";

    private final AllocationMapper reservationMapper;
    private final PoliceStationMapper policeStationMapper;
    private final DutySlotMapper dutySlotMapper;
    private final AllocationReminderTaskMapper reservationReminderTaskMapper;
    private final UserNotificationMapper userNotificationMapper;
    private final MessageOutboxMapper messageOutboxMapper;

    public AdminDashboardServiceImpl(AllocationMapper reservationMapper,
                                     PoliceStationMapper policeStationMapper,
                                     DutySlotMapper dutySlotMapper,
                                     AllocationReminderTaskMapper reservationReminderTaskMapper,
                                     UserNotificationMapper userNotificationMapper,
                                     MessageOutboxMapper messageOutboxMapper) {
        this.reservationMapper = reservationMapper;
        this.policeStationMapper = policeStationMapper;
        this.dutySlotMapper = dutySlotMapper;
        this.reservationReminderTaskMapper = reservationReminderTaskMapper;
        this.userNotificationMapper = userNotificationMapper;
        this.messageOutboxMapper = messageOutboxMapper;
    }

    @Override
    public AdminDashboardVO getOverview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime recentWindowStart = now.minusDays(7);

        AdminDashboardVO overview = new AdminDashboardVO();
        overview.setTotalAllocationCount(countReservations(null, null));
        overview.setTodayAllocationCount(countReservations(null, todayStart));
        overview.setActiveAllocationCount(countReservations(AllocationStatusConstants.BOOKED, null));
        overview.setFinishedAllocationCount(countReservations(AllocationStatusConstants.FINISHED, null));
        overview.setCancelledAllocationCount(countReservations(AllocationStatusConstants.CANCELLED, null));

        overview.setTotalStationCount(countResources(null));
        overview.setAvailableStationCount(countResources(RESOURCE_AVAILABLE));
        overview.setOpenSlotCount(countSlots(null, DutySlotStatusConstants.OPEN));
        overview.setHotOpenSlotCount(countSlots(DutySlotTypeConstants.HOT, DutySlotStatusConstants.OPEN));

        overview.setPendingReminderCount(countReminderTasks(STATUS_PENDING));
        overview.setUnreadNotificationCount(countUnreadNotifications());
        overview.setPendingAuditOutboxCount(countPendingAuditOutbox());

        overview.setHotSlots(buildHotSlots(now));
        overview.setTopStations(buildTopStations(recentWindowStart));
        return overview;
    }

    private long countReservations(String status, LocalDateTime createdFrom) {
        LambdaQueryWrapper<AllocationAssignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), AllocationAssignment::getStatus, status);
        wrapper.ge(createdFrom != null, AllocationAssignment::getCreatedAt, createdFrom);
        return reservationMapper.selectCount(wrapper);
    }

    private long countResources(String status) {
        return policeStationMapper.selectCount(new LambdaQueryWrapper<PoliceStation>()
                .eq(StringUtils.hasText(status), PoliceStation::getStatus, status));
    }

    private long countSlots(String slotType, String status) {
        return dutySlotMapper.selectCount(new LambdaQueryWrapper<DutySlot>()
                .eq(StringUtils.hasText(slotType), DutySlot::getSlotType, slotType)
                .eq(StringUtils.hasText(status), DutySlot::getStatus, status));
    }

    private long countReminderTasks(String status) {
        return reservationReminderTaskMapper.selectCount(new LambdaQueryWrapper<AllocationReminderTask>()
                .eq(StringUtils.hasText(status), AllocationReminderTask::getStatus, status));
    }

    private long countUnreadNotifications() {
        return userNotificationMapper.selectCount(new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getIsRead, 0));
    }

    private long countPendingAuditOutbox() {
        return messageOutboxMapper.selectCount(new LambdaQueryWrapper<MessageOutbox>()
                .eq(MessageOutbox::getEventType, ADMIN_AUDIT_LOG_EVENT_TYPE)
                .eq(MessageOutbox::getStatus, STATUS_PENDING));
    }

    private List<AdminDashboardHotSlotVO> buildHotSlots(LocalDateTime now) {
        List<DutySlot> hotSlots = dutySlotMapper.selectList(new LambdaQueryWrapper<DutySlot>()
                .eq(DutySlot::getSlotType, DutySlotTypeConstants.HOT)
                .eq(DutySlot::getStatus, DutySlotStatusConstants.OPEN)
                .ge(DutySlot::getEndDatetime, now)
                .orderByAsc(DutySlot::getStartDatetime)
                .last("LIMIT 6"));
        if (hotSlots.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, PoliceStation> resourceMap = loadStationMap(hotSlots.stream()
                .map(DutySlot::getStationId)
                .collect(Collectors.toSet()));

        return hotSlots.stream().map(slot -> {
            PoliceStation station = resourceMap.get(slot.getStationId());
            AdminDashboardHotSlotVO item = new AdminDashboardHotSlotVO();
            item.setSlotId(slot.getId());
            item.setStationId(slot.getStationId());
            if (station != null) {
                item.setStationName(station.getStationName());
                item.setStationCode(station.getStationCode());
            }
            item.setStartDatetime(slot.getStartDatetime());
            item.setEndDatetime(slot.getEndDatetime());
            item.setTotalQuota(slot.getTotalQuota());
            item.setRemainQuota(slot.getRemainQuota());
            int totalQuota = slot.getTotalQuota() == null ? 0 : slot.getTotalQuota();
            int remainQuota = slot.getRemainQuota() == null ? 0 : slot.getRemainQuota();
            int bookedQuota = Math.max(totalQuota - remainQuota, 0);
            item.setBookedQuota(bookedQuota);
            int occupancyRate = totalQuota <= 0 ? 0 : (int) Math.round(bookedQuota * 100.0 / totalQuota);
            item.setOccupancyRate(occupancyRate);
            item.setPressureLevel(toPressureLevel(occupancyRate));
            return item;
        }).collect(Collectors.toList());
    }

    private List<AdminDashboardStationHeatVO> buildTopStations(LocalDateTime recentWindowStart) {
        List<AllocationAssignment> allocations = reservationMapper.selectList(new LambdaQueryWrapper<AllocationAssignment>()
                .ge(AllocationAssignment::getCreatedAt, recentWindowStart)
                .orderByDesc(AllocationAssignment::getCreatedAt));
        if (allocations.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, PoliceStation> resourceMap = loadStationMap(allocations.stream()
                .map(AllocationAssignment::getStationId)
                .collect(Collectors.toSet()));

        Map<Long, List<AllocationAssignment>> grouped = allocations.stream()
                .collect(Collectors.groupingBy(AllocationAssignment::getStationId));

        return grouped.entrySet().stream()
                .map(entry -> {
                    PoliceStation station = resourceMap.get(entry.getKey());
                    if (station == null) {
                        return null;
                    }
                    List<AllocationAssignment> stationAllocations = entry.getValue();
                    AdminDashboardStationHeatVO item = new AdminDashboardStationHeatVO();
                    item.setStationId(station.getId());
                    item.setStationName(station.getStationName());
                    item.setStationCode(station.getStationCode());
                    item.setStationType(station.getStationType());
                    item.setAllocationCount((long) stationAllocations.size());
                    item.setActiveAllocationCount(stationAllocations.stream()
                            .filter(allocation -> AllocationStatusConstants.BOOKED.equals(allocation.getStatus()))
                            .count());
                    return item;
                })
                .filter(item -> item != null)
                .sorted(Comparator.comparing(AdminDashboardStationHeatVO::getAllocationCount, Comparator.reverseOrder())
                        .thenComparing(AdminDashboardStationHeatVO::getActiveAllocationCount, Comparator.reverseOrder())
                        .thenComparing(AdminDashboardStationHeatVO::getStationId))
                .limit(6)
                .collect(Collectors.toList());
    }

    private Map<Long, PoliceStation> loadStationMap(Set<Long> stationIds) {
        if (stationIds == null || stationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return policeStationMapper.selectBatchIds(stationIds).stream()
                .collect(Collectors.toMap(PoliceStation::getId, Function.identity()));
    }

    private String toPressureLevel(int occupancyRate) {
        if (occupancyRate >= 90) {
            return "HIGH";
        }
        if (occupancyRate >= 60) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
