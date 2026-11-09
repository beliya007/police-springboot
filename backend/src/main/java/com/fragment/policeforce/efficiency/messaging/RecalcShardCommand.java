package com.fragment.policeforce.efficiency.messaging;

/**
 * 效能重算命令。
 *
 * <p>一条命令对应<strong>一个分片</strong>，而不是一个警局。
 * 这是本项目消除批量重算读放大的关键：分片消息体里直接带上本片负责的警局 ID 列表，
 * 消费端不需要再回查数据库去确认「这条消息要算哪些警局」。</p>
 *
 * @param batchNo     批次号
 * @param shardIndex  分片序号
 * @param stationIds  本分片负责的警局 ID
 * @param calcVersion 本次计算版本（乐观 CAS 用）
 * @param triggerSource 触发来源
 * @param scopeType   触发范围
 */
public record RecalcShardCommand(String batchNo,
                                 Integer shardIndex,
                                 java.util.List<Long> stationIds,
                                 Long calcVersion,
                                 String triggerSource,
                                 String scopeType) {

    public RecalcShardCommand {
        stationIds = stationIds == null ? java.util.List.of() : java.util.List.copyOf(stationIds);
    }
}
