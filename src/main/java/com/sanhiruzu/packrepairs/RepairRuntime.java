package com.sanhiruzu.packrepairs;

import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RepairRuntime {
    private static final Logger LOG = LoggerFactory.getLogger("Pack Repairs");
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();
    private static final DataRepairEngine ENGINE = loadCatalog();

    private RepairRuntime() {}

    public static void initialize() {
        RepairConfig.enabled("*");
        LOG.info("Pack Repairs ready; supported mods are optional and resource repairs require a matching defect signature");
    }

    private static DataRepairEngine loadCatalog() {
        var catalog = new com.google.gson.JsonArray();
        try {
            for (String name : new String[]{"catalog.json", "loot-catalog.json", "recipe-compat-catalog.json"}) {
                try (var stream = RepairRuntime.class.getResourceAsStream("/packrepairs/" + name)) {
                    if (stream == null) throw new IllegalStateException("Missing repair catalog " + name);
                    catalog.addAll(JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray());
                }
            }
            return new DataRepairEngine(catalog);
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }

    public static boolean candidate(ResourceLocation id) { return ENGINE.candidate(id.toString()) || SignTemplateRepair.candidate(id.toString()) || TemplateContentRepairs.candidate(id.toString()); }

    public static Resource wrap(ResourceLocation id, Resource original) {
        if (!candidate(id)) return original;
        return new Resource(original.source(), () -> repair(id, original), original::metadata);
    }

    private static InputStream repair(ResourceLocation id, Resource original) throws IOException {
        byte[] bytes;
        try (var stream = original.open()) { bytes = stream.readAllBytes(); }
        if (TemplateContentRepairs.candidate(id.toString())) {
            var result = TemplateContentRepairs.repair(id.toString(), bytes);
            for (String repair : result.repairIds()) if (reported.add(repair + ":" + id + ":" + original.sourcePackId()))
                LOG.info("Applied {} to {} from {}", repair, id, original.sourcePackId());
            return new ByteArrayInputStream(result.bytes());
        }
        if (SignTemplateRepair.candidate(id.toString())) {
            var fixed = SignTemplateRepair.repair(id.toString(), bytes);
            if (fixed != bytes && reported.add(SignTemplateRepair.ID + ":" + id + ":" + original.sourcePackId()))
                LOG.info("Applied {} to {} from {}", SignTemplateRepair.ID, id, original.sourcePackId());
            return new ByteArrayInputStream(fixed);
        }
        var result = ENGINE.repair(id.toString(), bytes,
                RepairRuntime::dependencyMatches, RepairConfig::enabled,
                item -> BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(item)),
                OptionalConfigFlags::resolve);
        if (result.repairId() != null && reported.add(result.repairId() + ":" + id + ":" + original.sourcePackId())) {
            LOG.info("Applied {} to {} from {}", result.repairId(), id, original.sourcePackId());
        }
        return new ByteArrayInputStream(result.bytes());
    }

    private static boolean dependencyMatches(String requirement) {
        var list = ModList.get();
        if (list == null) return false;
        int separator = requirement.indexOf('@');
        if (separator < 0) return list.isLoaded(requirement);
        return list.getMods().stream().anyMatch(info -> info.getModId().equals(requirement.substring(0, separator))
                && info.getVersion().toString().equals(requirement.substring(separator + 1)));
    }
}
