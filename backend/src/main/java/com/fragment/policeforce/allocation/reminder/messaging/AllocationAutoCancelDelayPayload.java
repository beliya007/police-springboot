package com.fragment.policeforce.allocation.reminder.messaging;

import lombok.Data;

@Data
public class AllocationAutoCancelDelayPayload {

    private Long allocationId;

    public AllocationAutoCancelDelayPayload() {
    }

    public AllocationAutoCancelDelayPayload(Long allocationId) {
        this.allocationId = allocationId;
    }
}
