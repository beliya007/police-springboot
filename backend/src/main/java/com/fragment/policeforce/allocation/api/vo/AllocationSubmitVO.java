package com.fragment.policeforce.allocation.api.vo;

import lombok.Data;

@Data
public class AllocationSubmitVO {

    private String requestId;
    private String status;
    private Long allocationId;
    private String allocationNo;
    private String message;
}
