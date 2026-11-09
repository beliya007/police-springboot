package com.fragment.policeforce.efficiency.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计算版本号发号器。
 *
 * <p>版本号必须<strong>全局单调递增</strong>，因为它是结果覆盖的乐观 CAS 锚点：
 * 只有更大的版本号才能覆盖 {@code station_efficiency.latest_calc_version}。
 * 如果版本号在多实例下可能重复或倒退，就会出现「本次重算算完了，但结果被上一轮的
 * 旧版本号覆盖回去」的诡异 bug。</p>
 *
 * <p>用 MySQL 的自增主键模拟序列：每次分配插入一行、拿自增 id。
 * 自增主键由 InnoDB 保证单机严格递增且不回退，是这里最省事也最可靠的做法。</p>
 *
 * <p>注意事务传播：分配版本号必须<strong>独立提交</strong>，
 * 不能跟着外层业务事务回滚，否则批次失败后版本号会留下空洞。
 * 空洞本身无害（只要单调），但回滚导致的版本号复用会破坏 CAS 语义。</p>
 */
@Component
@RequiredArgsConstructor
public class CalcVersionAllocator {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 分配一个新的计算版本号。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long next() {
        jdbcTemplate.update("INSERT INTO calc_version_allocator (allocated_at) VALUES (NOW())");
        Long version = jdbcTemplate.queryForObject(
                "SELECT LAST_INSERT_ID()", Long.class);
        return version == null ? 0L : version;
    }
}
