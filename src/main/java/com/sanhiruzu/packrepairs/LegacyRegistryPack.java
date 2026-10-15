package com.sanhiruzu.packrepairs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.LoggerFactory;

/** Restores legacy registry names at the bottom of datapack priority; never rewrites saved NBT. */
public final class LegacyRegistryPack extends AbstractPackResources {
    private final Map<ResourceLocation, byte[]> contents;

    private LegacyRegistryPack(PackLocationInfo info, Map<ResourceLocation, byte[]> contents) {
        super(info); this.contents = Map.copyOf(contents);
    }

    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        var contents = new HashMap<ResourceLocation, byte[]>();
        try {
            if (RepairConfig.enabled("astralsorcery.legacy_focal_point_processor")) {
                byte[] current = verified("astralsorcery", "data/astralsorcery/worldgen/processor_list/focal_point_processors.json",
                        "52d79a3cfd4f7d10963e19f3e5273b0062115a75bf9d440c19e717cabaadeb9c");
                if (current != null && verified("astralsorcery",
                        "hellfirepvp/astralsorcery/common/worldgen/structure/processor/FocalPointRegisterProcessor.class",
                        "3092eda07efb676d64bac6de913e6d0c2da73ce03a3d20a5b767f1a38247078c") != null) {
                    var processors = JsonParser.parseString(new String(current, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("processors");
                    var legacy = new JsonArray();
                    processors.forEach(value -> {
                        if (value.getAsJsonObject().get("processor_type").getAsString().equals("astralsorcery:focal_point_register_processor")) legacy.add(value);
                    });
                    if (legacy.size() == 1) {
                        var json = new JsonObject(); json.add("processors", legacy);
                        add(contents, "astralsorcery", "worldgen/processor_list/focal_point_register_processor.json", json.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            if (RepairConfig.enabled("iceandfire.legacy_dread_banner_patterns")) {
                byte[] dread = verified("iceandfire", "data/iceandfire/banner_pattern/dread.json",
                        "ad9f21e68224ed9d20a0266351ee336d209e49e1d4c4889fe1e3373217230d5d");
                if (dread != null) for (String path : new String[]{"iaf_dread", "iceandfire.dread"})
                    add(contents, "minecraft", "banner_pattern/" + path + ".json", dread);
            }
            if (contents.isEmpty()) return;
            var location = new PackLocationInfo("packrepairs/legacy_registry_names", Component.literal("Legacy Registry Repairs"), PackSource.BUILT_IN, Optional.empty());
            var pack = Pack.readMetaAndCreate(location, BuiltInPackSource.fromName(info -> new LegacyRegistryPack(info, contents)),
                    PackType.SERVER_DATA, new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
            if (pack != null) {
                event.addRepositorySource(consumer -> consumer.accept(pack));
                LoggerFactory.getLogger("Pack Repairs").info("Applied legacy registry repairs: {} fallback resources", contents.size());
            }
        } catch (IOException | RuntimeException error) {
            LoggerFactory.getLogger("Pack Repairs").warn("Legacy registry repairs unavailable", error);
        }
    }

    private static byte[] verified(String mod, String path, String expected) throws IOException {
        var list = FMLLoader.getLoadingModList();
        var file = list == null ? null : list.getModFileById(mod);
        if (file == null) return null;
        var resource = file.getFile().findResource(path);
        if (!Files.isRegularFile(resource)) return null;
        byte[] bytes = Files.readAllBytes(resource);
        return DataRepairEngine.sha256(bytes).equals(expected) ? bytes : null;
    }

    private static void add(Map<ResourceLocation, byte[]> contents, String namespace, String path, byte[] bytes) {
        contents.put(ResourceLocation.fromNamespaceAndPath(namespace, path), bytes);
    }
    @Override public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length != 1 || !path[0].equals("pack.mcmeta")) return null;
        byte[] bytes = ("{\"pack\":{\"pack_format\":" + SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA)
                + ",\"description\":\"Legacy registry names\"}}").getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(bytes);
    }
    @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        byte[] bytes = type == PackType.SERVER_DATA ? contents.get(id) : null;
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }
    @Override public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
        if (type != PackType.SERVER_DATA) return;
        contents.forEach((id, bytes) -> {
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix + "/")) output.accept(id, () -> new ByteArrayInputStream(bytes));
        });
    }
    @Override public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? contents.keySet().stream().map(ResourceLocation::getNamespace).collect(java.util.stream.Collectors.toUnmodifiableSet()) : Set.of();
    }
    @Override public void close() {}
}
