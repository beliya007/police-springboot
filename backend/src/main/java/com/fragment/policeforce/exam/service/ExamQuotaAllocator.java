package com.fragment.policeforce.exam.service;

import com.fragment.policeforce.common.exception.BusinessException;
import com.fragment.policeforce.entity.StationEfficiency;
import com.fragment.policeforce.efficiency.persistence.StationEfficiencyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 体检名额分配器 —— 决定「体检名额优先给谁」。
 *
 * <p>业务规则：体检资源优先流向<strong>警力缺口最大</strong>的单位。
 * 分配算法是「按缺编比例做最大余额法分配」：</p>
 * <ol>
 *   <li>取所有警局的缺编人数 {@code shortage = configured - effective}；</li>
 *   <li>缺编为 0 的警局不参与分配（已满编）；</li>
 *   <li>按缺编比例把批次名额切成若干份，用「最大余额法」保证
 *       「分到的人数之和恰好等于名额总数」，且不超出各警局的缺编上限；</li>
 *   <li>余下的名额按缺编从大到小依次补齐。</li>
 * </ol>
 *
 * <p>最大余额法（largest remainder）的好处：分配结果之和恰好等于名额总数，
 * 不会出现「分完了还剩几个名额不知道给谁」的情况，也不会有小数误差。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamQuotaAllocator {

    private final StationEfficiencyMapper stationEfficiencyMapper;

    /**
     * 分配结果。
     *
     * @param quotas 警局 ID → 分到的名额数
     */
    public record QuotaAllocation(int totalQuota, java.util.Map<Long, Integer> quotas) {
    }

    /**
     * 按缺编比例分配体检名额。
     *
     * @param totalQuota 批次总名额
     * @return 各警局分到的名额
     */
    public QuotaAllocation allocate(int totalQuota) {
        if (totalQuota <= 0) {
            throw new BusinessException(400, "体检名额必须大于 0");
        }

        List<StationEfficiency> stations = stationEfficiencyMapper.selectList(null);
        // 只保留缺编为正的警局。
        List<StationEfficiency> candidates = new ArrayList<>();
        int totalShortage = 0;
        for (StationEfficiency station : stations) {
            Integer shortage = station.getShortageHeadcount();
            if (shortage != null && shortage > 0) {
                candidates.add(station);
                totalShortage += shortage;
            }
        }
        if (candidates.isEmpty()) {
            log.info("No station is understaffed, exam quota will not be distributed by shortage.");
            return new QuotaAllocation(totalQuota, java.util.Map.of());
        }

        // 缺编多的警局优先。
        candidates.sort(Comparator.comparing(StationEfficiency::getShortageHeadcount).reversed());

        // 第一轮：按缺编比例分配，向上取整前先用「最大余额法」确定整数分配。
        java.util.Map<Long, Integer> quotas = new java.util.LinkedHashMap<>();
        java.util.Map<Long, Double> remainders = new java.util.LinkedHashMap<>();
        int distributed = 0;
        for (StationEfficiency station : candidates) {
            int shortage = station.getShortageHeadcount();
            // 名额不能超过该警局的缺编上限，否则是给满编单位浪费资源。
            int quota = Math.min((int) Math.floor((double) totalQuota * shortage / totalShortage), shortage);
            quotas.put(station.getStationId(), quota);
            // 余额 = 理论份额 - 实际分配，用它决定谁能拿到「多出来的那一个名额」。
            remainders.put(station.getStationId(),
                    (double) totalQuota * shortage / totalShortage - quota);
            distributed += quota;
        }

        // 第二轮：把剩余名额按余额从大到小补齐，每个警局最多补到缺编上限。
        int remaining = totalQuota - distributed;
        if (remaining > 0) {
            List<StationEfficiency> byRemainder = new ArrayList<>(candidates);
            byRemainder.sort(Comparator.comparing(
                    (StationEfficiency station) -> remainders.get(station.getStationId())).reversed());
            boolean progressed = true;
            while (remaining > 0 && progressed) {
                progressed = false;
                for (StationEfficiency station : byRemainder) {
                    if (remaining <= 0) {
                        break;
                    }
                    Long stationId = station.getStationId();
                    int current = quotas.get(stationId);
                    if (current >= station.getShortageHeadcount()) {
                        continue;
                    }
                    quotas.put(stationId, current + 1);
                    remaining--;
                    progressed = true;
                }
            }
            // 第三轮：名额仍然有剩余（所有警局都满编了）→ 按缺编从大到小继续分配，
            // 允许超出缺编上限（宁可多做体检，也不要浪费体检资源）。
            while (remaining > 0 && !byRemainder.isEmpty()) {
                for (StationEfficiency station : byRemainder) {
                    if (remaining <= 0) {
                        break;
                    }
                    Long stationId = station.getStationId();
                    quotas.put(stationId, quotas.get(stationId) + 1);
                    remaining--;
                }
            }
        }

        log.info("Exam quota allocated. totalQuota={}, stations={}, distributed={}",
                totalQuota, quotas.size(), quotas.values().stream().mapToInt(Integer::intValue).sum());
        return new QuotaAllocation(totalQuota, quotas);
    }
}
