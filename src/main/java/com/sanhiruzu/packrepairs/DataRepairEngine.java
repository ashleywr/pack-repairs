package com.sanhiruzu.packrepairs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Function;

/** Independent of Minecraft so absence, drift and custom override behavior can be tested directly. */
public final class DataRepairEngine {
    public record Result(byte[] bytes, String repairId) {}
    private record Rule(String id, List<String> mods, String sha256, JsonArray operations) {}
    private final Map<String, List<Rule>> rules = new HashMap<>();

    public DataRepairEngine(JsonArray catalog) {
        for (var entry : catalog) {
            var object = entry.getAsJsonObject();
            var mods = new ArrayList<String>();
            object.getAsJsonArray("mods").forEach(value -> mods.add(value.getAsString()));
            rules.computeIfAbsent(object.get("resource").getAsString(), key -> new ArrayList<>())
                    .add(new Rule(object.get("id").getAsString(), List.copyOf(mods),
                            object.get("sha256").getAsString(), object.getAsJsonArray("operations")));
        }
    }

    public boolean candidate(String resource) {
        return rules.containsKey(resource)
                || (resource.startsWith("everycomp:recipe/") && resource.endsWith("_from_quark_hedge_crop.json"));
    }

    public Result repair(String resource, byte[] original, Predicate<String> modLoaded, Predicate<String> enabled) {
        return repair(resource, original, modLoaded, enabled, item -> true);
    }

    public Result repair(String resource, byte[] original, Predicate<String> modLoaded,
                         Predicate<String> enabled, Predicate<String> itemExists) {
        return repair(resource, original, modLoaded, enabled, itemExists, flag -> null);
    }

    public Result repair(String resource, byte[] original, Predicate<String> modLoaded,
                         Predicate<String> enabled, Predicate<String> itemExists,
                         Function<String, Boolean> flagValue) {
        for (var rule : rules.getOrDefault(resource, List.of())) {
            if (!enabled.test(rule.id) || !rule.mods.stream().allMatch(modLoaded)) continue;
            if (!sha256(original).equals(rule.sha256)) continue;
            var json = JsonParser.parseString(new String(original, StandardCharsets.UTF_8));
            boolean changed = false;
            for (var operation : rule.operations) changed |= apply(json, operation.getAsJsonObject(), itemExists, flagValue);
            if (changed) return new Result(json.toString().getBytes(StandardCharsets.UTF_8), rule.id);
        }
        if (resource.startsWith("everycomp:recipe/") && resource.endsWith("_from_quark_hedge_crop.json")
                && modLoaded.test("everycomp@1.21-2.11.52") && modLoaded.test("botanypots@21.1.44")
                && enabled.test("everycomp.botanypots_hedges")) {
            try {
                var root = JsonParser.parseString(new String(original, StandardCharsets.UTF_8)).getAsJsonObject();
                if (legacyHedgeCrop(root)) {
                    root.add("input", root.remove("seed"));
                    root.add("grow_time", root.remove("growthTicks"));
                    root.remove("categories");
                    var soil = new JsonObject();
                    soil.addProperty("tag", "botanypots:soil/dirt");
                    root.add("soil", soil);
                    var display = new JsonObject();
                    display.addProperty("type", "botanypots:simple");
                    display.add("block_state", root.get("display"));
                    root.add("display", display);
                    var items = new JsonArray();
                    for (var element : root.getAsJsonArray("drops")) {
                        var old = element.getAsJsonObject();
                        var drop = new JsonObject();
                        var stack = old.getAsJsonObject("output").deepCopy();
                        stack.add("id", stack.remove("item"));
                        drop.add("result", stack);
                        drop.add("chance", old.get("chance"));
                        items.add(drop);
                    }
                    var drops = new JsonObject();
                    drops.addProperty("type", "botanypots:items");
                    drops.add("items", items);
                    root.add("drops", drops);
                    return new Result(root.toString().getBytes(StandardCharsets.UTF_8), "everycomp.botanypots_hedges");
                }
            } catch (RuntimeException ignored) {
                // Unrecognized data is returned unchanged; the native loader supplies the diagnostic.
            }
        }
        return new Result(original, null);
    }

    private static boolean legacyHedgeCrop(JsonObject root) {
        if (!root.keySet().equals(java.util.Set.of("type", "seed", "categories", "growthTicks", "display", "drops"))) return false;
        if (!root.get("type").getAsString().equals("botanypots:crop")) return false;
        if (!root.getAsJsonObject("seed").keySet().equals(java.util.Set.of("item"))) return false;
        if (!root.getAsJsonObject("display").keySet().equals(java.util.Set.of("block"))) return false;
        if (!root.get("categories").equals(JsonParser.parseString("[\"dirt\"]"))) return false;
        if (!root.get("growthTicks").isJsonPrimitive() || !root.get("categories").isJsonArray()) return false;
        if (root.getAsJsonArray("drops").isEmpty()) return false;
        for (var element : root.getAsJsonArray("drops")) {
            var drop = element.getAsJsonObject();
            if (!drop.keySet().equals(java.util.Set.of("chance", "output"))) return false;
            if (!drop.getAsJsonObject("output").keySet().equals(java.util.Set.of("item"))) return false;
        }
        return true;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static boolean apply(JsonElement root, JsonObject operation, Predicate<String> itemExists,
                                 Function<String, Boolean> flagValue) {
        String path = operation.get("path").getAsString();
        String[] pieces = path.substring(1).split("/");
        JsonElement parent = root;
        for (int index = 0; index < pieces.length - 1; index++) parent = child(parent, unescape(pieces[index]));
        String key = unescape(pieces[pieces.length - 1]);
        String action = operation.get("op").getAsString();
        if (action.equals("resolve_fabric_flag")) {
            Boolean active = flagValue.apply(operation.get("flag").getAsString());
            if (active == null) return false; // Unknown code signature: leave the upstream diagnostic intact.
            var condition = new JsonObject();
            condition.addProperty("condition", "fabric:true");
            if (!active) {
                var inverse = new JsonObject();
                inverse.addProperty("condition", "fabric:not");
                inverse.add("value", condition);
                condition = inverse;
                // Also honor the flag when Fabric's condition loader is absent on native NeoForge.
                root.getAsJsonObject().add(operation.get("inactiveKey").getAsString(), new JsonArray());
            }
            var conditions = new JsonArray();
            conditions.add(condition);
            parent.getAsJsonObject().add(key, conditions);
        } else if (action.equals("empty_if_missing_item")) {
            var entry = child(parent, key).getAsJsonObject();
            if (!entry.get("type").getAsString().equals("minecraft:item"))
                throw new IllegalArgumentException("Expected an item loot entry at " + path);
            if (itemExists.test(entry.get("name").getAsString())) return false;
            // Empty is a weighted singleton too: retain odds, conditions, functions and pool rolls.
            entry.addProperty("type", "minecraft:empty");
            entry.remove("name");
        } else if (action.equals("rename")) {
            var object = parent.getAsJsonObject();
            object.add(operation.get("to").getAsString(), object.remove(key));
        } else if (action.equals("remove")) {
            parent.getAsJsonObject().remove(key);
        } else if (action.equals("replace") || action.equals("add")) {
            JsonElement value = operation.get("value").deepCopy();
            if (parent.isJsonArray()) parent.getAsJsonArray().set(Integer.parseInt(key), value);
            else parent.getAsJsonObject().add(key, value);
        } else throw new IllegalArgumentException("Unsupported repair operation " + action);
        return true;
    }

    private static JsonElement child(JsonElement parent, String key) {
        return parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(key)) : parent.getAsJsonObject().get(key);
    }

    private static String unescape(String key) { return key.replace("~1", "/").replace("~0", "~"); }
}
