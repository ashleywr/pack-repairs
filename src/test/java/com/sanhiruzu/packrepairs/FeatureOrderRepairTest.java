package com.sanhiruzu.packrepairs;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeatureOrderRepairTest {
    @Test void recognizesTheObservedSharedFeatureReversal() {
        assertTrue(FeatureOrderRepair.hasReversedOrder(List.of(List.of("dense_grass", "moss_grass"), List.of("moss_grass", "dense_grass"))));
        assertFalse(FeatureOrderRepair.hasReversedOrder(List.of(List.of("dense_grass", "moss_grass"), List.of("dense_grass", "moss_grass"))));
    }

    @Test void retainsMultiplicityAndUnrelatedOrderAndIsIdempotent() {
        var original = List.of("other:c", "bloomingnature:b", "other:a", "bloomingnature:a", "other:c");
        var fixed = FeatureOrderRepair.order(original, value -> value.startsWith("bloomingnature:"), Comparator.naturalOrder());
        assertEquals(List.of("other:c", "other:a", "other:c", "bloomingnature:a", "bloomingnature:b"), fixed);
        assertEquals(original.size(), fixed.size());
        assertEquals(fixed, FeatureOrderRepair.order(fixed, value -> value.startsWith("bloomingnature:"), Comparator.naturalOrder()));
        assertEquals(original, FeatureOrderRepair.order(original, value -> false, Comparator.naturalOrder()));
    }
}
