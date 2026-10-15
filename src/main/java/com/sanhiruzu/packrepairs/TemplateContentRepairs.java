package com.sanhiruzu.packrepairs;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

public final class TemplateContentRepairs {
    private static final TemplateRepairEngine ENGINE = load();
    private TemplateContentRepairs() {}
    private static TemplateRepairEngine load() {
        try (var input = TemplateContentRepairs.class.getResourceAsStream("/packrepairs/template-catalog.json")) {
            if (input == null) throw new IOException("Missing template repair catalog");
            return new TemplateRepairEngine(JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray());
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }
    public static boolean candidate(String id) { return ENGINE.candidate(id); }
    public static TemplateRepairEngine.Result repair(String id, byte[] bytes) throws IOException {
        var mods = ModList.get();
        return ENGINE.repair(id, bytes, mod -> mods != null && mods.isLoaded(mod), RepairConfig::enabled,
                item -> BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(item)),
                entity -> BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(entity)));
    }
}
