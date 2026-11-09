package com.fragment.policeforce.allocation.support;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationNoGeneratorTest {

    private final AllocationNoGenerator generator = new AllocationNoGenerator();

    @Test
    void shouldGeneratePrefixedUniqueNumbersWithIdWorker() {
        Set<String> numbers = new HashSet<>();

        for (int i = 0; i < 1_000; i++) {
            String allocationNo = generator.nextAllocationNo();
            String requestNo = generator.nextRequestNo();
            assertTrue(allocationNo.matches("RES\\d+"));
            assertTrue(requestNo.matches("REQ\\d+"));
            numbers.add(allocationNo);
            numbers.add(requestNo);
        }

        assertEquals(2_000, numbers.size());
    }
}
