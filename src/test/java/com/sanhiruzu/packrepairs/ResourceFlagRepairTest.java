package com.sanhiruzu.packrepairs;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceFlagRepairTest {
    private static final String RESOURCE = "sawmill:rs_pool_additions/fixture.json";
    private static final byte[] ORIGINAL = bytes("""
            {"fabric:load_conditions":[{"condition":"sawmill:flag","flag":"rs_compat"}],
             "target_pool":"repurposed_structures:villages/fixture/houses","fallback":"minecraft:empty",
             "elements":[{"weight":5,"element":{"location":"sawmill:fixture"}}]}
            """);

    private static DataRepairEngine engine() {
        var rule = JsonParser.parseString("""
                {"id":"sawmill.repurposed_structures_condition","mods":["sawmill@1.21-1.8.1"],
                 "resource":"sawmill:rs_pool_additions/fixture.json","operations":[
                   {"op":"resolve_fabric_flag","path":"/fabric:load_conditions",
                    "flag":"sawmill.rs_compat","inactiveKey":"elements"}]}
                """).getAsJsonObject();
        rule.addProperty("sha256", DataRepairEngine.sha256(ORIGINAL));
        var catalog = new JsonArray(); catalog.add(rule);
        return new DataRepairEngine(catalog);
    }

    @Test void enabledFlagPreservesEveryPoolElementAndWeight() {
        var result = engine().repair(RESOURCE, ORIGINAL, mod -> true, repair -> true,
                item -> true, flag -> true);
        var expected = JsonParser.parseString(new String(ORIGINAL, StandardCharsets.UTF_8)).getAsJsonObject();
        expected.add("fabric:load_conditions", JsonParser.parseString("[{\"condition\":\"fabric:true\"}]"));
        assertEquals(expected, JsonParser.parseString(new String(result.bytes(), StandardCharsets.UTF_8)));
    }

    @Test void disabledFlagSuppressesContentWithAndWithoutFabricLoader() {
        var result = engine().repair(RESOURCE, ORIGINAL, mod -> true, repair -> true,
                item -> true, flag -> false);
        var json = JsonParser.parseString(new String(result.bytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(json.getAsJsonArray("elements").isEmpty());
        assertEquals(JsonParser.parseString("[{\"condition\":\"fabric:not\",\"value\":{\"condition\":\"fabric:true\"}}]"),
                json.get("fabric:load_conditions"));
        assertEquals("minecraft:empty", json.get("fallback").getAsString());
        assertEquals("repurposed_structures:villages/fixture/houses", json.get("target_pool").getAsString());
    }

    @Test void unknownSignatureMissingOwnerDisabledRepairAndCustomDataRemainUnchanged() {
        assertSame(ORIGINAL, engine().repair(RESOURCE, ORIGINAL, mod -> true, repair -> true,
                item -> true, flag -> null).bytes());
        assertSame(ORIGINAL, engine().repair(RESOURCE, ORIGINAL, mod -> false, repair -> true,
                item -> true, flag -> true).bytes());
        assertSame(ORIGINAL, engine().repair(RESOURCE, ORIGINAL, mod -> true, repair -> false,
                item -> true, flag -> true).bytes());
        var custom = bytes(new String(ORIGINAL, StandardCharsets.UTF_8).replace("\"weight\":5", "\"weight\":37"));
        assertSame(custom, engine().repair(RESOURCE, custom, mod -> true, repair -> true,
                item -> true, flag -> true).bytes());
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
