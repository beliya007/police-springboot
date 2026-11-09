package com.fragment.policeforce.allocation.service;

import com.fragment.policeforce.allocation.redis.HotAllocationRedisService;
import com.fragment.policeforce.allocation.reminder.AllocationAutoCancelService;
import com.fragment.policeforce.allocation.messaging.AllocationResultEvent;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.persistence.AllocationMapper;
import com.fragment.policeforce.allocation.reminder.AllocationReminderTaskService;
import com.fragment.policeforce.service.UserNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预约结果的收尾
 */
@Service
public class AllocationResultService {

    private final HotAllocationRedisService hotRedis;      // Redis 状态机（把请求收敛成终态）
    private final AllocationMapper reservationMapper;      // 预约记录表
    private final AllocationReminderTaskService reminderService;  // 开课提醒任务
    private final AllocationAutoCancelService autoCancelService;  // 未签到自动取消
    private final UserNotificationService notificationService;     // 站内通知

    public AllocationResultService(HotAllocationRedisService hotRedis,
                                    AllocationMapper reservationMapper,
                                    AllocationReminderTaskService reminderService,
                                    AllocationAutoCancelService autoCancelService,
                                    UserNotificationService notificationService) {
        this.hotRedis = hotRedis;
        this.reservationMapper = reservationMapper;
        this.reminderService = reminderService;
        this.autoCancelService = autoCancelService;
        this.notificationService = notificationService;
    }

    /**
     * 处理一条预约结果事件。整体在事务内，任何一步失败整个回滚，靠 MQ 重投再试。
     *
     * 流程：校验 → 收敛Redis → 发通知 → (仅成功时)建提醒 + 排自动取消。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(String eventId, AllocationResultEvent event) {
        validate(eventId, event);
        // ① 收敛 Redis：把请求 hash 的状态改成终态；若结果是 REJECTED，
        //    complete 脚本会顺手释放用户占位并把库存 +1（把预占的名额还回去）。
        hotRedis.complete(event);
        // ② 发站内通知：不管成功还是失败都要告诉用户结果。
        notificationService.createAllocationResultNotification(eventId, event);

        // ③ 只有"预约成功(CONFIRMED)"才需要后续的提醒和自动取消。
        //    被拒绝的请求到此为止（Redis 已回补，通知已发）。
        if (!AllocationRequestStatus.CONFIRMED.name().equals(event.status())) {
            return;
        }
        // ④ 成功路径：查出刚落库的预约记录。
        AllocationAssignment allocation = reservationMapper.selectById(event.allocationId());
        if (allocation == null) {
            // 消费端说成功了、但记录不存在 → 数据异常，抛异常让事务回滚、等 MQ 重投。
            throw new IllegalStateException("Confirmed allocation does not exist: " + event.allocationId());
        }
        // ⑤ 建一条"开课前提醒"任务。
        reminderService.createBeforeStartReminder(allocation);
        // ⑥ 安排"未签到自动取消"任务（时间到还没签到就自动取消，释放名额）。
        autoCancelService.schedule(allocation);
    }

    /** 入参校验：必要字段缺了就拒绝，避免脏数据流入。 */
    private void validate(String eventId, AllocationResultEvent event) {
        if (eventId == null || event == null || event.requestId() == null
                || event.userId() == null || event.slotId() == null || event.status() == null) {
            throw new IllegalArgumentException("Invalid allocation result event");
        }
    }
}
