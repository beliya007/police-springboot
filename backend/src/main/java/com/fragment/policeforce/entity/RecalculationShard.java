package com.fragment.policeforce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 效能重算批次分片表。
 *
 * <p>批量重算惊群场景下，
 * 「每个警局一条消息」会导致 N 次全表扫描（N 次读放大）。
 * 改为「每个分片一条消息、每片只扫一次表在内存里算多个警局」，
 * 把 N 次全表扫描塌缩为 {@code N/并发度} 次。</p>
 */
@TableName(value = "recalculation_shard")
@Data
public class RecalculationShard {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "batch_no")
    private String batchNo;

    @TableField(value = "shard_index")
    private Integer shardIndex;

    /**
     * 本分片负责的警局 ID 列表（JSON 数组）
     */
    @TableField(value = "station_ids")
    private String stationIds;

    @TableField(value = "station_count")
    private Integer stationCount;

    /**
     * 状态：PENDING 待处理 / PROCESSING 处理中 / DONE 已完成 / FAILED 失败
     */
    @TableField(value = "status")
    private String status;

    @TableField(value = "retry_count")
    private Integer retryCount;

    @TableField(value = "last_error_message")
    private String lastErrorMessage;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
