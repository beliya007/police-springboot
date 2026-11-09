package com.fragment.policeforce.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fragment.policeforce.common.auth.UserContext;
import com.fragment.policeforce.common.result.Result;
import com.fragment.policeforce.dto.NotificationPageQueryDTO;
import com.fragment.policeforce.service.UserNotificationService;
import com.fragment.policeforce.vo.UserNotificationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification")
public class NotificationController {

    @Autowired
    private UserNotificationService userNotificationService;

    @GetMapping("/page")
    public Result<Page<UserNotificationVO>> pageMyNotifications(NotificationPageQueryDTO queryDTO) {
        Long userId = UserContext.requireUser().getId();
        return Result.success(userNotificationService.pageMyNotifications(userId, queryDTO));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        Long userId = UserContext.requireUser().getId();
        return Result.success(userNotificationService.countUnread(userId));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = UserContext.requireUser().getId();
        userNotificationService.markRead(userId, id);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        Long userId = UserContext.requireUser().getId();
        userNotificationService.markAllRead(userId);
        return Result.success();
    }
}
