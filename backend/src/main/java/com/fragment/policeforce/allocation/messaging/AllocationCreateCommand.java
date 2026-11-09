package com.fragment.policeforce.allocation.messaging;

public record AllocationCreateCommand(
        String requestId,
        Long userId,
        Long officerId,
        Long stationId,
        Long slotId
) {
}
