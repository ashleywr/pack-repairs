package com.sanhiruzu.packrepairs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/** Pure ordering logic: retain every entry and the relative order of unrelated features. */
public final class FeatureOrderRepair {
    private record Pair(String before, String after) {}

    public static boolean hasReversedOrder(List<List<String>> rows) {
        var orders = new HashSet<Pair>();
        for (var row : rows) {
            for (int left = 0; left < row.size(); left++) {
                for (int right = left + 1; right < row.size(); right++) {
                    var pair = new Pair(row.get(left), row.get(right));
                    if (orders.contains(new Pair(pair.after, pair.before))) return true;
                    orders.add(pair);
                }
            }
        }
        return false;
    }

    public static <T> List<T> order(List<T> original, Predicate<T> affected, Comparator<T> comparator) {
        var result = new ArrayList<T>();
        var repaired = new ArrayList<T>();
        for (T value : original) {
            if (affected.test(value)) repaired.add(value);
            else result.add(value);
        }
        repaired.sort(comparator);
        result.addAll(repaired);
        return result;
    }
}
