package com.fragment.policeforce.exam.api.dto;

import jakarta.validation.constraints.Size;

/**
 * 取消体检预约请求。
 */
public class ExamCancelDTO {

    /**
     * 取消原因
     */
    @Size(max = 255, message = "取消原因不能超过 255 个字符")
    private String cancelReason;

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
