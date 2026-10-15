package com.sanhiruzu.packrepairs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptionalLootRepairTest {
    private static final String RESOURCE = "idas:loot_table/chests/fixture.json";
    private static final byte[] ORIGINAL = bytes("""
            {"type":"minecraft:chest","random_sequence":"idas:fixture","pools":[
              {"rolls":3,"bonus_rolls":0.25,"entries":[
                {"type":"minecraft:item","name":"minecraft:diamond","weight":7},
                {"type":"minecraft:item","name":"optional:gem","weight":2,"quality":4,
                 "conditions":[{"condition":"minecraft:random_chance","chance":0.5}],
                 "functions":[{"function":"minecraft:set_count","count":2}]},
                {"type":"minecraft:item","name":"optional:ingot","weight":5}]}]}
            """);

    private static DataRepairEngine engine() {
        var rule = JsonParser.parseString("""
                {"id":"idas.optional_loot_items","mods":["idas"],
                 "resource":"idas:loot_table/chests/fixture.json","operations":[
                   {"op":"empty_if_missing_item","path":"/pools/0/entries/1"},
                   {"op":"empty_if_missing_item","path":"/pools/0/entries/2"}]}
                """).getAsJsonObject();
        rule.addProperty("sha256", DataRepairEngine.sha256(ORIGINAL));
        var catalog = new JsonArray();
        catalog.add(rule);
        return new DataRepairEngine(catalog);
    }

    @Test void absentIntegrationRetainsOtherLootAndOriginalSelectionProbabilities() {
        var result = engine().repair(RESOURCE, ORIGINAL, mod -> true, repair -> true,
                item -> !item.startsWith("optional:"));
        var expected = json(ORIGINAL);
        var entries = expected.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
        for (int i : new int[]{1, 2}) {
            entries.get(i).getAsJsonObject().addProperty("type", "minecraft:empty");
            entries.get(i).getAsJsonObject().remove("name");
        }
        assertEquals(expected, json(result.bytes()));
        assertEquals("idas.optional_loot_items", result.repairId());
        assertSame(result.bytes(), engine().repair(RESOURCE, result.bytes(), mod -> true,
                repair -> true, item -> false).bytes());
    }

    @Test void installedOrRestoredIntegrationLeavesUpstreamBytesUnchanged() {
        var result = engine().repair(RESOURCE, ORIGINAL, mod -> true, repair -> true, item -> true);
        assertSame(ORIGINAL, result.bytes());
        assertNull(result.repairId());
    }

    @Test void partialIntegrationChangesOnlyItsMissingItem() {
        var result = engine().repair(RESOURCE, ORIGINAL, mod -> true, repair -> true,
                item -> !item.equals("optional:ingot"));
        var originalEntries = json(ORIGINAL).getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
        var repairedEntries = json(result.bytes()).getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
        assertEquals(originalEntries.get(0), repairedEntries.get(0));
        assertEquals(originalEntries.get(1), repairedEntries.get(1));
        assertEquals("minecraft:empty", repairedEntries.get(2).getAsJsonObject().get("type").getAsString());
    }

    @Test void missingOwnerDisabledRepairAndCustomOverrideAreUntouched() {
        assertSame(ORIGINAL, engine().repair(RESOURCE, ORIGINAL, mod -> false, repair -> true, item -> false).bytes());
        assertSame(ORIGINAL, engine().repair(RESOURCE, ORIGINAL, mod -> true, repair -> false, item -> false).bytes());
        var custom = json(ORIGINAL);
        custom.addProperty("random_sequence", "custom:fixture");
        var changed = bytes(custom.toString());
        assertSame(changed, engine().repair(RESOURCE, changed, mod -> true, repair -> true, item -> false).bytes());
    }

    @Test void jewelryCatalogRepairsOnlyTagKeysAndKeepsMaterialChoices() throws Exception {
        JsonArray catalog;
        try (var stream = getClass().getResourceAsStream("/packrepairs/loot-catalog.json")) {
            assertNotNull(stream);
            catalog = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
        }
        assertEquals(286, catalog.size());
        var rule = catalog.asList().stream().map(value -> value.getAsJsonObject())
                .filter(value -> value.get("id").getAsString().equals("irons_jewelry.material_filter_tags"))
                .findFirst().orElseThrow().deepCopy();
        var original = bytes("""
                {"pools":[{"entries":[{"functions":[{"materials":{
                  "#irons_jewelry:metal":"irons_jewelry:gold",
                  "#irons_jewelry:gem":["irons_jewelry:ruby","irons_jewelry:diamond"]}}]}]}]}
                """);
        rule.addProperty("sha256", DataRepairEngine.sha256(original));
        var fixture = new JsonArray();
        fixture.add(rule);
        var engine = new DataRepairEngine(fixture);
        var result = engine.repair(rule.get("resource").getAsString(), original, mod -> true, repair -> true);
        var materials = json(result.bytes()).getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("functions").get(0)
                .getAsJsonObject().getAsJsonObject("materials");
        assertEquals(Set.of("irons_jewelry:metal", "irons_jewelry:gem"), materials.keySet());
        assertEquals("irons_jewelry:gold", materials.get("irons_jewelry:metal").getAsString());
        assertEquals(JsonParser.parseString("[\"irons_jewelry:ruby\",\"irons_jewelry:diamond\"]"), materials.get("irons_jewelry:gem"));
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static JsonObject json(byte[] value) {
        return JsonParser.parseString(new String(value, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
