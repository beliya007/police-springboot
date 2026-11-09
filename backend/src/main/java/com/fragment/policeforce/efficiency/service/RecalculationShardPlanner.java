package com.fragment.policeforce.efficiency.service;

import com.fragment.policeforce.entity.PoliceStation;
import com.fragment.policeforce.mapper.PoliceStationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 重算分片规划器。
 *
 * <p>这是本项目相对开源项目最关键的一层设计。批量重算惊群的本质问题是
 * <strong>读放大</strong>：如果「每个警局一条消息、每条消息查一次全表」，
 * 那么 N 个警局就是 N 次全表扫描，数据库直接被读垮。</p>
 *
 * <p>本项目的做法是「每分片一条消息、每片只扫一次表在内存里算多个警局」，
 * 把 N 次全表扫描塌缩为 <code>ceil(N / shardSize)</code> 次，
 * 读放大与分片数成反比，与警局数无关。</p>
 *
 * <p>分片大小的取舍：分片越大 → 扫描次数越少、单片耗时越长、失败重投代价越大；
 * 分片越小 → 反之。默认 20 是「扫描次数足够少」与「单片足够短」之间的折中。</p>
 */
@Service
@RequiredArgsConstructor
public class RecalculationShardPlanner {

    private final PoliceStationMapper stationMapper;

    /**
     * 一个分片覆盖多少个警局。
     */
    @Value("${app.efficiency.command.shard-size:20}")
    private int shardSize;

    /**
     * 单个批次的最大分片数，防止警局数量失控时把 MQ 打满。
     */
    @Value("${app.efficiency.command.max-shards:64}")
    private int maxShards;

    /**
     * 一个分片的规划结果。
     *
     * @param shardIndex 分片序号
     * @param stationIds 该分片负责的警局 ID
     */
    public record ShardPlan(int shardIndex, List<Long> stationIds) {
    }

    /**
     * 按范围规划分片。
     *
     * @param scopeType 范围类型：ALL / DISTRICT / STATION
     * @param scopeId   范围目标 ID（STATION 时为警局 ID）
     * @param district  区域名（DISTRICT 时使用）
     */
    public List<ShardPlan> plan(String scopeType, Long scopeId, String district) {
        List<PoliceStation> stations = resolveStations(scopeType, scopeId, district);
        return plan(stations.stream().map(PoliceStation::getId).toList());
    }

    /**
     * 把警局 ID 列表切成若干分片。
     */
    public List<ShardPlan> plan(List<Long> stationIds) {
        List<ShardPlan> plans = new ArrayList<>();
        if (stationIds == null || stationIds.isEmpty()) {
            return plans;
        }
        int size = Math.max(1, shardSize);
        int shardCount = 0;
        for (int start = 0; start < stationIds.size(); start += size) {
            int end = Math.min(start + size, stationIds.size());
            if (shardCount >= maxShards) {
                // 超过上限时把剩余警局并入最后一个分片，而不是丢弃：
                // 丢弃会让部分警局的效能永远停留在旧版本。
                List<Long> merged = new ArrayList<>(plans.get(plans.size() - 1).stationIds());
                merged.addAll(stationIds.subList(start, stationIds.size()));
                plans.set(plans.size() - 1,
                        new ShardPlan(plans.get(plans.size() - 1).shardIndex(), merged));
                break;
            }
            plans.add(new ShardPlan(shardCount, new ArrayList<>(stationIds.subList(start, end))));
            shardCount++;
        }
        return plans;
    }

    /**
     * 解析重算范围对应的警局列表。
     */
    private List<PoliceStation> resolveStations(String scopeType, Long scopeId, String district) {
        if (EfficiencyConstants.SCOPE_STATION.equals(scopeType)) {
            if (scopeId == null) {
                return List.of();
            }
            PoliceStation station = stationMapper.selectById(scopeId);
            return station == null ? List.of() : List.of(station);
        }
        if (EfficiencyConstants.SCOPE_DISTRICT.equals(scopeType)) {
            if (district == null || district.isBlank()) {
                return List.of();
            }
            return stationMapper.selectByDistrict(district);
        }
        return stationMapper.selectAllForCalculation();
    }
}
