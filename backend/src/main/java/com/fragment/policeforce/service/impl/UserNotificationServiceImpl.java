package com.fragment.policeforce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fragment.policeforce.dto.NotificationPageQueryDTO;
import com.fragment.policeforce.allocation.messaging.AllocationResultEvent;
import com.fragment.policeforce.allocation.model.AllocationAssignment;
import com.fragment.policeforce.allocation.model.AllocationReminderTask;
import com.fragment.policeforce.entity.UserNotification;
import com.fragment.policeforce.mapper.UserNotificationMapper;
import com.fragment.policeforce.service.UserNotificationService;
import com.fragment.policeforce.vo.UserNotificationVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class UserNotificationServiceImpl extends ServiceImpl<UserNotificationMapper, UserNotification>
        implements UserNotificationService {

    private static final String TYPE_RESERVATION_REMINDER = "RESERVATION_REMINDER";
    private static final String TYPE_RESERVATION_AUTO_CANCEL = "RESERVATION_AUTO_CANCEL";
    private static final String TYPE_RESERVATION_CONFIRMED = "RESERVATION_CONFIRMED";
    private static final String TYPE_RESERVATION_REJECTED = "RESERVATION_REJECTED";
    private static final String TYPE_EXAM_CONFIRMED = "EXAM_CONFIRMED";
    private static final String TYPE_EXAM_REJECTED = "EXAM_REJECTED";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public Page<UserNotificationVO> pageMyNotifications(Long userId, NotificationPageQueryDTO queryDTO) {
        NotificationPageQueryDTO actualQuery = queryDTO == null ? new NotificationPageQueryDTO() : queryDTO;
        Integer readFlag = null;
        if (actualQuery.getRead() != null) {
            readFlag = actualQuery.getRead() ? 1 : 0;
        }

        long pageNum = actualQuery.getPageNum() == null ? 1L : actualQuery.getPageNum();
        long pageSize = actualQuery.getPageSize() == null ? 10L : actualQuery.getPageSize();

        Page<UserNotification> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .eq(readFlag != null, UserNotification::getIsRead, readFlag)
                .orderByAsc(UserNotification::getIsRead)
                .orderByDesc(UserNotification::getCreatedAt)
                .orderByDesc(UserNotification::getId);

        Page<UserNotification> entityPage = this.page(page, wrapper);
        Page<UserNotificationVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    public long countUnread(Long userId) {
        return this.count(new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 0));
    }

    @Override
    public void markRead(Long userId, Long id) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<UserNotification> updateWrapper = new LambdaUpdateWrapper<UserNotification>()
                .eq(UserNotification::getId, id)
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 0)
                .set(UserNotification::getIsRead, 1)
                .set(UserNotification::getReadAt, now);
        this.update(updateWrapper);
    }

    @Override
    public void markAllRead(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<UserNotification> updateWrapper = new LambdaUpdateWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 0)
                .set(UserNotification::getIsRead, 1)
                .set(UserNotification::getReadAt, now);
        this.update(updateWrapper);
    }

    @Override
    public void createReminderNotification(AllocationReminderTask task) {
        if (task == null) {
            return;
        }

        UserNotification notification = new UserNotification();
        notification.setUserId(task.getUserId());
        notification.setType(TYPE_RESERVATION_REMINDER);
        notification.setTitle(task.getTitle());
        notification.setContent(task.getContent());
        notification.setRelatedAllocationId(task.getAllocationId());
        notification.setReminderTaskId(task.getId());
        notification.setIsRead(0);
        this.save(notification);
    }

    @Override
    public void createAutoCancelNotification(AllocationAssignment allocation) {
        if (allocation == null) {
            return;
        }

        UserNotification notification = new UserNotification();
        notification.setUserId(allocation.getUserId());
        notification.setType(TYPE_RESERVATION_AUTO_CANCEL);
        notification.setTitle("预约已自动取消");
        notification.setContent(buildAutoCancelContent(allocation));
        notification.setRelatedAllocationId(allocation.getId());
        notification.setIsRead(0);
        this.save(notification);
    }

    @Override
    public void createAllocationResultNotification(String eventId, AllocationResultEvent event) {
        if (eventId == null || event == null || event.userId() == null) {
            return;
        }
        UserNotification notification = new UserNotification();
        notification.setEventId(eventId);
        notification.setUserId(event.userId());
        notification.setType("CONFIRMED".equals(event.status())
                ? TYPE_RESERVATION_CONFIRMED : TYPE_RESERVATION_REJECTED);
        notification.setTitle("CONFIRMED".equals(event.status()) ? "预约成功" : "预约未通过");
        notification.setContent("CONFIRMED".equals(event.status())
                ? "预约已确认，预约编号：" + event.allocationNo()
                : (event.rejectReason() == null ? "预约请求未通过最终确认" : event.rejectReason()));
        notification.setRelatedAllocationId(event.allocationId());
        notification.setIsRead(0);
        try {
            this.save(notification);
        } catch (DuplicateKeyException ignored) {
            // event_id makes result-event redelivery idempotent.
        }
    }

    @Override
    public void createExamResultNotification(String eventId, Long userId, Long appointmentId,
                                             boolean confirmed, String message) {
        if (eventId == null || userId == null) {
            return;
        }
        UserNotification notification = new UserNotification();
        notification.setEventId(eventId);
        notification.setUserId(userId);
        notification.setType(confirmed ? TYPE_EXAM_CONFIRMED : TYPE_EXAM_REJECTED);
        notification.setTitle(confirmed ? "体检预约成功" : "体检预约未通过");
        notification.setContent(message);
        notification.setRelatedExamAppointmentId(appointmentId);
        notification.setIsRead(0);
        try {
            this.save(notification);
        } catch (DuplicateKeyException ignored) {
            // event_id makes result-event redelivery idempotent.
        }
    }

    private UserNotificationVO toVO(UserNotification notification) {
        UserNotificationVO vo = new UserNotificationVO();
        BeanUtils.copyProperties(notification, vo);
        vo.setRead(notification.getIsRead() != null && notification.getIsRead() == 1);
        return vo;
    }

    private String buildAutoCancelContent(AllocationAssignment allocation) {
        String stationName = allocation.getStationName() == null ? "您预约的资源" : allocation.getStationName();
        String startText = allocation.getSlotStartDatetime() == null
                ? "预约开始后"
                : allocation.getSlotStartDatetime().format(DATETIME_FORMATTER);
        return "您预约的资源“" + stationName + "”在 " + startText + " 后未完成签到，系统已自动取消并释放名额。";
    }
}
