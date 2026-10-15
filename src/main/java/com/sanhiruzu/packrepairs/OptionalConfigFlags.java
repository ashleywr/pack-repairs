package com.sanhiruzu.packrepairs;

import java.lang.reflect.Modifier;
import java.util.function.Supplier;
import net.neoforged.fml.ModList;

/** Optional config access without linking supported mods into Pack Repairs' class signatures. */
final class OptionalConfigFlags {
    private static final String SAWMILL_CONFIG = "net.mehvahdjukaar.sawmill.CommonConfigs";
    private static final String SAWMILL_SIGNATURE = "8c7dde0dde3fc742f89d5b135b9f8f4b13d310f5b98baf68c47ac7e484cbc6dd";

    private OptionalConfigFlags() {}

    static Boolean resolve(String flag) {
        if (!flag.equals("sawmill.rs_compat")) return null;
        var mods = ModList.get();
        if (mods == null || !mods.isLoaded("sawmill")) return null;
        if (!mods.isLoaded("repurposed_structures")) return false;
        var loader = OptionalConfigFlags.class.getClassLoader();
        try (var stream = loader.getResourceAsStream(SAWMILL_CONFIG.replace('.', '/') + ".class")) {
            if (stream == null || !DataRepairEngine.sha256(stream.readAllBytes()).equals(SAWMILL_SIGNATURE)) return null;
            var field = Class.forName(SAWMILL_CONFIG, false, loader).getField("RS_COMPAT");
            if (!Modifier.isStatic(field.getModifiers()) || !field.getType().equals(Supplier.class)) return null;
            var value = ((Supplier<?>) field.get(null)).get();
            return value instanceof Boolean enabled ? enabled : null;
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError unsupported) {
            return null;
        }
    }
}
