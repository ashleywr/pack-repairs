package com.sanhiruzu.packrepairs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.LoggerFactory;

/** Low-priority virtual assets let ordinary resource-pack replacements win. */
public final class BuddycardsRulebookPack extends AbstractPackResources {
    private final Map<String, byte[]> contents;

    private BuddycardsRulebookPack(PackLocationInfo location, Map<String, byte[]> contents) {
        super(location);
        this.contents = contents;
    }

    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        try {
            var contents = BuddycardsRulebookRepair.contents();
            if (contents.isEmpty()) return;
            var location = new PackLocationInfo("packrepairs/buddycards_rulebook", Component.literal("Buddycards Rulebook Repair"),
                    PackSource.BUILT_IN, Optional.empty());
            var pack = Pack.readMetaAndCreate(location,
                    BuiltInPackSource.fromName(info -> new BuddycardsRulebookPack(info, contents)),
                    PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
            if (pack == null) return;
            event.addRepositorySource(consumer -> consumer.accept(pack));
            LoggerFactory.getLogger("Pack Repairs").info("Applied {}: exposed {} original book resources as client assets",
                    BuddycardsRulebookRepair.ID, contents.size());
        } catch (IOException error) {
            LoggerFactory.getLogger("Pack Repairs").warn("Buddycards rulebook migration unavailable", error);
        }
    }

    @Override public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length != 1 || !"pack.mcmeta".equals(path[0])) return null;
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        byte[] bytes = ("{\"pack\":{\"pack_format\":" + format + ",\"description\":\"Buddycards rulebook migration\"}}")
                .getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(bytes);
    }

    @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        if (type != PackType.CLIENT_RESOURCES || !"buddycards".equals(id.getNamespace())) return null;
        byte[] bytes = contents.get(id.getPath());
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !"buddycards".equals(namespace)) return;
        contents.forEach((path, bytes) -> {
            if (path.startsWith(prefix + "/")) output.accept(ResourceLocation.fromNamespaceAndPath(namespace, path),
                    () -> new ByteArrayInputStream(bytes));
        });
    }

    @Override public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of("buddycards") : Set.of();
    }
    @Override public void close() {}
}
