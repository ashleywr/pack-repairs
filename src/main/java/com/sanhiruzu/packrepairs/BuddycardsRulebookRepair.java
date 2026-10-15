package com.sanhiruzu.packrepairs;

import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.fml.loading.FMLLoader;
import org.slf4j.LoggerFactory;

/** Migrate only the fingerprinted Buddycards book; never copy upstream text into this mod. */
public final class BuddycardsRulebookRepair {
    public static final String ID = "buddycards.patchouli_rulebook_resources";
    public static final String BOOK = "buddycards:buddycards_rules";
    public static final String PREFIX = "patchouli_books/buddycards_rules/";
    public static final Map<String, String> HASHES = loadHashes();
    private static final String REGISTRY_HASH = "fa3108d7eb23d3bd2029eb1d9a7aa61ca3497ec921dee004b554c7f219f66244";

    private BuddycardsRulebookRepair() {}

    private static Map<String, String> loadHashes() {
        try (var stream = BuddycardsRulebookRepair.class.getResourceAsStream("/packrepairs/buddycards-book-hashes.json")) {
            if (stream == null) throw new IllegalStateException("Missing Buddycards book fingerprints");
            var result = new LinkedHashMap<String, String>();
            JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()
                    .entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
            return Map.copyOf(result);
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }

    public static boolean matches(String path, byte[] bytes) {
        return DataRepairEngine.sha256(bytes).equals(HASHES.get(path));
    }

    /** Called during optional mixin selection and client pack registration. */
    public static boolean supported() {
        if (!RepairConfig.enabled(ID)) return false;
        var list = FMLLoader.getLoadingModList();
        if (list == null) return false;
        var buddycards = list.getModFileById("buddycards");
        var patchouli = list.getModFileById("patchouli");
        if (buddycards == null || patchouli == null) return false;
        try {
            if (!DataRepairEngine.sha256(Files.readAllBytes(patchouli.getFile().findResource(
                    "vazkii/patchouli/common/book/BookRegistry.class"))).equals(REGISTRY_HASH)) return false;
            for (var entry : HASHES.entrySet()) {
                if (!matches(entry.getKey(), Files.readAllBytes(buddycards.getFile().findResource(
                        "data/buddycards/" + entry.getKey())))) return false;
                // An upstream resource migration must take precedence over this fallback.
                if (!entry.getKey().endsWith("/book.json") && Files.exists(buddycards.getFile().findResource(
                        "assets/buddycards/" + entry.getKey()))) return false;
            }
            return true;
        } catch (IOException | RuntimeException ignored) { return false; }
    }

    public static byte[] migrateDeclaration(String id, boolean external, byte[] bytes) {
        if (external || !BOOK.equals(id) || !matches(PREFIX + "book.json", bytes)) return bytes;
        var book = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        book.addProperty("use_resource_pack", true);
        return book.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static InputStream declaration(String id, boolean external, InputStream stream) throws IOException {
        if (!BOOK.equals(id) || external || !RepairConfig.enabled(ID)) return stream;
        byte[] original = stream.readAllBytes();
        byte[] migrated = migrateDeclaration(id, false, original);
        if (migrated != original) LoggerFactory.getLogger("Pack Repairs").info("Applied {} to {} declaration", ID, BOOK);
        return new ByteArrayInputStream(migrated);
    }

    /** Original contents are read from the installed optional mod, not shipped in Pack Repairs. */
    public static Map<String, byte[]> contents() throws IOException {
        if (!supported()) return Map.of();
        var file = FMLLoader.getLoadingModList().getModFileById("buddycards").getFile();
        var result = new LinkedHashMap<String, byte[]>();
        for (String path : HASHES.keySet()) {
            if (path.endsWith("/book.json")) continue;
            byte[] bytes = Files.readAllBytes(file.findResource("data/buddycards/" + path));
            if (!matches(path, bytes)) return Map.of();
            result.put(path, bytes);
        }
        return Map.copyOf(result);
    }
}
