package com.sanhiruzu.packrepairs;

import com.google.gson.JsonParser;
import com.sanhiruzu.packrepairs.mixin.ModifiableBiomeInfoAccessor;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.LoggerFactory;

public final class BloomingNatureRepair {
    private static final String ID = "bloomingnature.added_feature_order";
    private BloomingNatureRepair() {}

    public static void aboutToStart(ServerAboutToStartEvent event) {
        if (!ModList.get().isLoaded("bloomingnature") || !RepairConfig.enabled(ID)) return;
        var server = event.getServer();
        // A custom/fixed replacement of any relevant Blooming Nature resource opts out of this group.
        try (var stream = BloomingNatureRepair.class.getResourceAsStream("/packrepairs/bloomingnature-order-guards.json")) {
            if (stream == null) return;
            var guards = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            for (var entry : guards) {
                var guard = entry.getAsJsonObject();
                var resource = server.getResourceManager().getResource(ResourceLocation.parse(guard.get("resource").getAsString()));
                if (resource.isEmpty()) return;
                try (var input = resource.get().open()) {
                    String hash = DataRepairEngine.sha256(input.readAllBytes());
                    if (!hash.equals(guard.get("sha256").getAsString())
                            && (!guard.has("repair_sha256") || !hash.equals(guard.get("repair_sha256").getAsString()))) {
                        LoggerFactory.getLogger("Pack Repairs").info("Skipped {}: customized or changed resource {}", ID, guard.get("resource").getAsString());
                        return;
                    }
                }
            }
        } catch (Exception exception) {
            LoggerFactory.getLogger("Pack Repairs").warn("Skipped {}: could not verify selected Blooming Nature resources", ID, exception);
            return;
        }

        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        var features = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        var ids = new HashMap<PlacedFeature, String>();
        features.entrySet().forEach(entry -> ids.put(entry.getValue(), entry.getKey().location().toString()));
        var candidates = biomes.holders().filter(holder -> holder.is(BiomeTags.IS_OVERWORLD)
                || holder.key().location().getNamespace().equals("bloomingnature")).toList();
        var rows = new HashMap<Integer, List<List<String>>>();
        for (Holder.Reference<Biome> holder : candidates) {
            var stages = holder.value().getGenerationSettings().features();
            for (int stage = 0; stage < stages.size(); stage++) {
                var names = stages.get(stage).stream().map(value -> ids.getOrDefault(value.value(), ""))
                        .filter(name -> name.startsWith("bloomingnature:")).toList();
                rows.computeIfAbsent(stage, key -> new ArrayList<>()).add(names);
            }
        }
        if (rows.values().stream().noneMatch(FeatureOrderRepair::hasReversedOrder)) return;

        int count = 0;
        Comparator<Holder<PlacedFeature>> byName = Comparator.comparing(holder -> ids.get(holder.value()));
        for (Holder.Reference<Biome> holder : candidates) {
            var biome = holder.value();
            var info = biome.modifiableBiomeInfo();
            var current = info.get();
            var builder = new BiomeGenerationSettingsBuilder(current.generationSettings());
            boolean changed = false;
            for (var step : GenerationStep.Decoration.values()) {
                var list = builder.getFeatures(step);
                var fixed = FeatureOrderRepair.order(list,
                        feature -> ids.getOrDefault(feature.value(), "").startsWith("bloomingnature:"), byName);
                if (!fixed.equals(list)) {
                    list.clear();
                    list.addAll(fixed);
                    changed = true;
                }
            }
            if (changed) {
                ((ModifiableBiomeInfoAccessor) info).packrepairs$setModifiedBiomeInfo(new ModifiableBiomeInfo.BiomeInfo(
                        current.climateSettings(), current.effects(), builder.build(), current.mobSpawnSettings()));
                count++;
            }
        }
        server.registryAccess().registryOrThrow(Registries.LEVEL_STEM)
                .forEach(stem -> stem.generator().refreshFeaturesPerStep());
        LoggerFactory.getLogger("Pack Repairs").info("Applied {} to {} overworld biomes; feature counts, stages and unrelated feature order retained", ID, count);
    }
}
