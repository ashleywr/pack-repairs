package com.sanhiruzu.packrepairs;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Read before optional mixins are selected, so disabling a code repair takes effect at startup. */
public final class RepairConfig {
    private static Set<String> disabled;

    private RepairConfig() {}

    public static synchronized boolean enabled(String id) {
        if (disabled == null) {
            var entries = new HashSet<String>();
            Path path = Path.of("config", "packrepairs.json");
            try {
                if (Files.exists(path)) {
                    var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    if (root.has("disabledRepairs")) {
                        for (var value : root.getAsJsonArray("disabledRepairs")) entries.add(value.getAsString());
                    }
                } else {
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, "{\n  \"disabledRepairs\": []\n}\n");
                }
            } catch (Exception error) {
                // A malformed config must not silently activate patches the pack author intended to disable.
                entries.add("*");
                System.getLogger("Pack Repairs").log(System.Logger.Level.WARNING,
                        "Cannot read packrepairs.json; all repairs are disabled", error);
            }
            disabled = Set.copyOf(entries);
        }
        return !disabled.contains("*") && !disabled.contains(id);
    }
}
