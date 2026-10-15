package com.sanhiruzu.packrepairs;

import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureCountMergeTest {
    @Test void overlappingModsRetainAllPiecesAndDisabledContributorCannotEraseThem() {
        var counts = new HashMap<String, List<String>>();
        var priests = List.of("priest=1");
        var sawmill = List.of("sawmill_big=1", "sawmill_small=1");
        assertNull(StructureCountMerge.put(counts, "village", priests));
        assertSame(priests, StructureCountMerge.put(counts, "village", sawmill));
        StructureCountMerge.put(counts, "village", List.of("waystone=1"));
        StructureCountMerge.put(counts, "village", List.of());
        assertEquals(List.of("priest=1", "sawmill_big=1", "sawmill_small=1", "waystone=1"), counts.get("village"));
        assertEquals(List.of("priest=1"), priests);
        assertEquals(List.of("sawmill_big=1", "sawmill_small=1"), sawmill);
    }

    @Test void independentStructuresRemainSeparateAndConflictsKeepNativeLastContributorOrder() {
        var counts = new HashMap<String, List<String>>();
        StructureCountMerge.put(counts, "badlands", List.of("house=1"));
        StructureCountMerge.put(counts, "jungle", List.of("treehouse=2"));
        StructureCountMerge.put(counts, "badlands", List.of("house=3"));
        assertEquals(List.of("house=1", "house=3"), counts.get("badlands"));
        assertEquals(List.of("treehouse=2"), counts.get("jungle"));
    }
}
