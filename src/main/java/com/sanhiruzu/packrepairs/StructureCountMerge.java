package com.sanhiruzu.packrepairs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Retain each contributor's pieces; native per-piece conflict handling keeps the last value. */
public final class StructureCountMerge {
    private StructureCountMerge() {}

    public static <K, T> List<T> put(Map<K, List<T>> counts, K structure, List<T> incoming) {
        var existing = counts.get(structure);
        if (existing == null) return counts.put(structure, incoming);
        var combined = new ArrayList<T>(existing.size() + incoming.size());
        combined.addAll(existing);
        combined.addAll(incoming);
        return counts.put(structure, combined);
    }
}
