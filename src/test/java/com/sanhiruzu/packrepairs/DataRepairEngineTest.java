package com.sanhiruzu.packrepairs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataRepairEngineTest {
    private static final String RESOURCE = "hexerei:loot_table/blocks/willow_vines.json";
    private static final byte[] BROKEN = bytes("{\"condition\":\"forge:can_tool_perform_action\",\"action\":\"shears_dig\",\"count\":4}");

    private DataRepairEngine fixture() {
        var catalog = new JsonArray();
        var rule = JsonParser.parseString("""
                {"id":"hexerei.willow_vines_loot","mods":["hexerei"],
                 "resource":"hexerei:loot_table/blocks/willow_vines.json", "operations":[
                   {"op":"replace","path":"/condition","value":"neoforge:can_item_perform_ability"},
                   {"op":"rename","path":"/action","to":"ability"}]}
                """).getAsJsonObject();
        rule.addProperty("sha256", DataRepairEngine.sha256(BROKEN));
        catalog.add(rule);
        return new DataRepairEngine(catalog);
    }

    @Test void absentModDoesNothing() {
        var result = fixture().repair(RESOURCE, BROKEN, mod -> false, repair -> true);
        assertSame(BROKEN, result.bytes());
        assertNull(result.repairId());
    }

    @Test void disabledRepairDoesNothing() {
        assertSame(BROKEN, fixture().repair(RESOURCE, BROKEN, mod -> true, repair -> false).bytes());
    }

    @Test void recognizedDefectChangesOnlyTheBrokenFieldsAndIsIdempotent() {
        var engine = fixture();
        var result = engine.repair(RESOURCE, BROKEN, mod -> true, repair -> true);
        var expected = JsonParser.parseString("{\"condition\":\"neoforge:can_item_perform_ability\",\"ability\":\"shears_dig\",\"count\":4}");
        assertEquals(expected, JsonParser.parseString(new String(result.bytes(), StandardCharsets.UTF_8)));
        assertEquals("hexerei.willow_vines_loot", result.repairId());
        assertSame(result.bytes(), engine.repair(RESOURCE, result.bytes(), mod -> true, repair -> true).bytes());
    }

    @Test void customOrUpstreamChangedResourceRemainsByteForByteUnchanged() {
        byte[] customized = bytes(new String(BROKEN, StandardCharsets.UTF_8).replace("4", "7"));
        assertSame(customized, fixture().repair(RESOURCE, customized, mod -> true, repair -> true).bytes());
        byte[] fixed = bytes("{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}}");
        assertSame(fixed, fixture().repair(RESOURCE, fixed, mod -> true, repair -> true).bytes());
    }

    @Test void unrelatedResourceAndInvalidUnknownDataAreUntouched() {
        assertSame(BROKEN, fixture().repair("anothermod:loot_table/blocks/willow_vines.json", BROKEN, mod -> true, repair -> true).bytes());
        byte[] invalid = bytes("not json");
        assertSame(invalid, fixture().repair("everycomp:recipe/a_from_quark_hedge_crop.json", invalid, mod -> true, repair -> true).bytes());
    }

    private static byte[] hedge() {
        return bytes("""
                {"type":"botanypots:crop","seed":{"item":"quark:oak_hedge"},"categories":["dirt"],
                 "growthTicks":1800,"display":{"block":"quark:oak_hedge"},
                 "drops":[{"chance":1,"output":{"item":"minecraft:oak_leaves"}}]}
                """);
    }

    @Test void legacyGeneratedHedgeRecipeRetainsItsIngredientsTimeAndYield() {
        var engine = new DataRepairEngine(new JsonArray());
        var result = engine.repair("everycomp:recipe/q/test/oak_from_quark_hedge_crop.json", hedge(), mod -> true, repair -> true);
        assertEquals("everycomp.botanypots_hedges", result.repairId());
        var fixed = JsonParser.parseString(new String(result.bytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1800, fixed.get("grow_time").getAsInt());
        assertEquals("quark:oak_hedge", fixed.getAsJsonObject("input").get("item").getAsString());
        assertEquals("quark:oak_hedge", fixed.getAsJsonObject("display").getAsJsonObject("block_state").get("block").getAsString());
        var drop = fixed.getAsJsonObject("drops").getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("minecraft:oak_leaves", drop.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, drop.get("chance").getAsInt());
        assertSame(result.bytes(), engine.repair("everycomp:recipe/q/test/oak_from_quark_hedge_crop.json", result.bytes(), mod -> true, repair -> true).bytes());
    }

    @Test void otherBotanyVersionsOrCustomizedSchemaAreNotConverted() {
        var engine = new DataRepairEngine(new JsonArray());
        var original = hedge();
        assertSame(original, engine.repair("everycomp:recipe/oak_from_quark_hedge_crop.json", original,
                mod -> !mod.startsWith("botanypots@"), repair -> true).bytes());
        var custom = JsonParser.parseString(new String(original, StandardCharsets.UTF_8)).getAsJsonObject();
        custom.addProperty("custom_property", true);
        var bytes = bytes(custom.toString());
        assertSame(bytes, engine.repair("everycomp:recipe/oak_from_quark_hedge_crop.json", bytes, mod -> true, repair -> true).bytes());
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
