package com.fragment.policeforce.allocation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fragment.policeforce.allocation.model.AllocationStatusConstants;
import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.allocation.api.dto.AllocationPageQueryDTO;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.entity.SysUser;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.service.PoliceStationService;
import com.fragment.policeforce.service.SysUserService;
import com.fragment.policeforce.allocation.api.vo.AllocationVO;
import com.fragment.policeforce.allocation.api.vo.UserAllocationOverviewVO;
import com.fragment.policeforce.vo.UserVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AllocationQueryService {

    private final AllocationMapper reservationMapper;
    private final SysUserService userService;
    private final PoliceStationService policeStationService;

    public AllocationQueryService(AllocationMapper reservationMapper,
                                   SysUserService userService,
                                   PoliceStationService policeStationService) {
        this.reservationMapper = reservationMapper;
        this.userService = userService;
        this.policeStationService = policeStationService;
    }

    public List<AllocationVO> listByUserId(Long userId) {
        return toViews(listForUser(userId));
    }

    public Page<AllocationVO> page(AllocationPageQueryDTO query) {
        AllocationPageQueryDTO actual = query == null ? new AllocationPageQueryDTO() : query;
        Page<AllocationAssignment> source = reservationMapper.selectPage(
                new Page<>(actual.getPageNum() == null ? 1 : actual.getPageNum(),
                        actual.getPageSize() == null ? 10 : actual.getPageSize()),
                pageFilter(actual));
        Page<AllocationVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(toViews(source.getRecords()));
        return result;
    }

    public AllocationVO getById(Long userId, boolean admin, Long id) {
        if (id == null) throw new BusinessException("预约ID不能为空");
        AllocationAssignment allocation = reservationMapper.selectById(id);
        if (allocation == null) throw new BusinessException("预约记录不存在");
        if (!admin && !allocation.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权查看他人的预约详情");
        }
        return toViews(List.of(allocation)).get(0);
    }

    public UserAllocationOverviewVO getUserOverview(Long userId) {
        SysUser user = requireUser(userId);
        List<AllocationAssignment> allocations = listForUser(userId);
        Map<Long, PoliceStation> stations = loadStations(allocations);

        UserVO userView = new UserVO();
        BeanUtils.copyProperties(user, userView);
        UserAllocationOverviewVO result = new UserAllocationOverviewVO();
        result.setUser(userView);
        result.setTotalAllocationCount((long) allocations.size());
        result.setActiveAllocationCount(countStatus(allocations, AllocationStatusConstants.BOOKED));
        result.setFinishedAllocationCount(countStatus(allocations, AllocationStatusConstants.FINISHED));
        result.setCancelledAllocationCount(countStatus(allocations, AllocationStatusConstants.CANCELLED));
        result.setRecent30DayAllocationCount(allocations.stream()
                .filter(item -> item.getCreatedAt() != null
                        && !item.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30))).count());
        result.setLatestAllocationAt(allocations.stream().map(AllocationAssignment::getCreatedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
        result.setFavoriteStationName(mostFrequent(allocations.stream()
                .map(AllocationAssignment::getStationName)
                .filter(StringUtils::hasText).toList()));
        result.setFavoriteStationType(mostFrequent(allocations.stream()
                .map(item -> resourceValue(stations, item.getStationId(), PoliceStation::getStationType))
                .filter(StringUtils::hasText).toList()));
        result.setFavoriteSourceType(mostFrequent(allocations.stream().map(AllocationAssignment::getSourceType)
                .filter(StringUtils::hasText).toList()));
        result.setFavoriteTimeBucket(mostFrequent(allocations.stream()
                .map(AllocationAssignment::getSlotStartDatetime)
                .map(this::timeBucket)
                .filter(StringUtils::hasText).toList()));
        return result;
    }

    public Page<AllocationVO> pageByUser(Long userId, AllocationPageQueryDTO query) {
        requireUser(userId);
        AllocationPageQueryDTO actual = new AllocationPageQueryDTO();
        if (query != null) {
            BeanUtils.copyProperties(query, actual);
        }
        actual.setUserId(userId);
        return page(actual);
    }

    private List<AllocationAssignment> listForUser(Long userId) {
        return reservationMapper.selectList(new LambdaQueryWrapper<AllocationAssignment>()
                .eq(AllocationAssignment::getUserId, userId)
                .orderByDesc(AllocationAssignment::getCreatedAt).orderByDesc(AllocationAssignment::getId));
    }

    private LambdaQueryWrapper<AllocationAssignment> pageFilter(AllocationPageQueryDTO query) {
        return new LambdaQueryWrapper<AllocationAssignment>()
                .eq(query.getUserId() != null, AllocationAssignment::getUserId, query.getUserId())
                .eq(query.getStationId() != null, AllocationAssignment::getStationId, query.getStationId())
                .eq(StringUtils.hasText(query.getStatus()), AllocationAssignment::getStatus, query.getStatus())
                .ge(query.getCreatedFrom() != null, AllocationAssignment::getCreatedAt, query.getCreatedFrom())
                .le(query.getCreatedTo() != null, AllocationAssignment::getCreatedAt, query.getCreatedTo())
                .orderByDesc(AllocationAssignment::getCreatedAt).orderByDesc(AllocationAssignment::getId);
    }

    private SysUser requireUser(Long userId) {
        if (userId == null) throw new BusinessException("用户ID不能为空");
        SysUser user = userService.getById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        return user;
    }

    private Map<Long, PoliceStation> loadStations(List<AllocationAssignment> allocations) {
        Set<Long> ids = allocations.stream().map(AllocationAssignment::getStationId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : policeStationService.listByIds(ids).stream()
                .collect(Collectors.toMap(PoliceStation::getId, Function.identity()));
    }

    /**
     * 使用预约快照转换接口视图，并批量补充当前用户资料。
     */
    private List<AllocationVO> toViews(List<AllocationAssignment> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> users = loadUsers(allocations);
        return allocations.stream().map(item -> toView(item, users.get(item.getUserId()))).toList();
    }

    /** 批量查询用户，避免预约列表逐条查用户造成 N+1。 */
    private Map<Long, SysUser> loadUsers(List<AllocationAssignment> allocations) {
        Set<Long> ids = allocations.stream().map(AllocationAssignment::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : userService.listByIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    /**
     * 资源与时段信息来自创建预约时保存的快照，避免资源修改后历史记录发生变化。
     */
    private AllocationVO toView(AllocationAssignment allocation, SysUser user) {
        AllocationVO view = new AllocationVO();
        BeanUtils.copyProperties(allocation, view);
        view.setLocation(allocation.getStationLocation());
        view.setStartDatetime(allocation.getSlotStartDatetime());
        view.setEndDatetime(allocation.getSlotEndDatetime());
        view.setCheckedIn(allocation.getCheckedInAt() != null);
        if (user != null) {
            view.setUserNickname(user.getNickname());
            view.setUserPhone(user.getPhone());
        }
        return view;
    }

    private long countStatus(List<AllocationAssignment> allocations, String status) {
        return allocations.stream().filter(item -> status.equals(item.getStatus())).count();
    }

    private String resourceValue(Map<Long, PoliceStation> stations, Long id,
                                 Function<PoliceStation, String> extractor) {
        PoliceStation station = stations.get(id);
        return station == null ? null : extractor.apply(station);
    }

    private String timeBucket(LocalDateTime time) {
        if (time == null) return null;
        if (time.getHour() < 12) return "MORNING";
        return time.getHour() < 18 ? "AFTERNOON" : "EVENING";
    }

    private String mostFrequent(List<String> values) {
        if (values.isEmpty()) return null;
        Map<String, Long> counts = new HashMap<>();
        values.forEach(value -> counts.merge(value, 1L, Long::sum));
        return counts.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse(null);
    }
}
