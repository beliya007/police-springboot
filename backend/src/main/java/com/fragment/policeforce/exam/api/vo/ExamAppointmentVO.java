package com.fragment.policeforce.exam.api.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 体检预约单展示对象。
 */
@Data
public class ExamAppointmentVO {

    private Long id;

    private String appointmentNo;

    private Long officerId;

    private String officerName;

    private Long slotId;

    private Long batchId;

    private LocalDateTime slotStartDatetime;

    private LocalDateTime slotEndDatetime;

    /**
     * BOOKED / CANCELLED / CHECKED_IN / COMPLETED
     */
    private String status;

    /**
     * 来源：NORMAL 普通通道 / HOT 热门通道
     */
    private String sourceType;

    /**
     * 是否由候补递补产生
     */
    private Boolean fromWaiting;

    private LocalDateTime createdAt;
}
