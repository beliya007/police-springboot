package com.fragment.policeforce.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fragment.policeforce.dto.NotificationPageQueryDTO;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.model.AllocationReminderTask;
import com.fragment.policeforce.entity.UserNotification;
import com.fragment.policeforce.allocation.messaging.AllocationResultEvent;
import com.fragment.policeforce.vo.UserNotificationVO;

public interface UserNotificationService extends IService<UserNotification> {

    Page<UserNotificationVO> pageMyNotifications(Long userId, NotificationPageQueryDTO queryDTO);

    long countUnread(Long userId);

    void markRead(Long userId, Long id);

    void markAllRead(Long userId);

    void createReminderNotification(AllocationReminderTask task);

    void createAutoCancelNotification(AllocationAssignment allocation);

    void createAllocationResultNotification(String eventId, AllocationResultEvent event);

    /**
     * 体检预约结果通知。
     *
     * @param eventId        事件 ID（幂等键）
     * @param userId         接收人（登录账号）
     * @param appointmentId  体检预约单 ID
     * @param confirmed      是否预约成功
     * @param message        提示文案
     */
    void createExamResultNotification(String eventId, Long userId, Long appointmentId,
                                      boolean confirmed, String message);
}
