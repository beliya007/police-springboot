package com.fragment.policeforce.allocation.messaging;

public record AllocationResultEvent(
        String requestId,
        Long userId,
        Long officerId,
        Long stationId,
        Long slotId,
        String status,
        Long allocationId,
        String allocationNo,
        String rejectCode,
        String rejectReason
) {
}
