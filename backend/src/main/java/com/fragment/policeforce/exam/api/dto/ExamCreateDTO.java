package com.fragment.policeforce.exam.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 体检预约请求。
 */
public class ExamCreateDTO {

    /**
     * 体检时段 ID
     */
    @NotNull(message = "体检时段不能为空")
    private Long slotId;

    /**
     * 警员 ID。仅管理员需要显式指定；普通用户由登录账号自动映射。
     */
    private Long officerId;

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public Long getOfficerId() {
        return officerId;
    }

    public void setOfficerId(Long officerId) {
        this.officerId = officerId;
    }
}
