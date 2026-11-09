package com.fragment.policeforce.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserNotificationVO {

    private Long id;
    private String type;
    private String title;
    private String content;
    private Long relatedAllocationId;
    private Boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
