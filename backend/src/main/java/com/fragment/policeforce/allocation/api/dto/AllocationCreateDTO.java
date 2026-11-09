package com.fragment.policeforce.allocation.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AllocationCreateDTO {

    @NotNull(message = "资源ID不能为空")
    private Long stationId;

    @NotNull(message = "时段ID不能为空")
    private Long slotId;

    /**
     * 被调配的警员 ID。
     *
     * <p>警力调配的业务主体是「警员」而不是「登录用户」：
     * 同一名警员不能被两个单位同时派出，这是第二重互斥，
     * 由 {@code uk_allocation_officer_slot_active} 唯一键在数据库层兜底。</p>
     *
     * <p>普通用户不传时，服务端按登录账号映射到绑定的警员。</p>
     */
    private Long officerId;
}
