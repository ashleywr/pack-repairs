package com.sanhiruzu.packrepairs;

import com.google.gson.JsonArray;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/** Selection is separate from NBT traversal so absent/restored integrations are evaluated on every reload. */
public final class TemplateRepairEngine {
    public record Result(byte[] bytes, int changes, Set<String> repairIds) {}
    private record Rule(String mod, String hash, List<ConditionalEdit> edits) {}
    private record ConditionalEdit(TemplateNbtPatcher.Edit edit, String repair, String item, String entity) {}
    private final Map<String, Rule> rules = new HashMap<>();

    public TemplateRepairEngine(JsonArray catalog) {
        catalog.forEach(value -> {
            var json = value.getAsJsonObject(); var edits = new ArrayList<ConditionalEdit>();
            json.getAsJsonArray("edits").forEach(element -> {
                var e = element.getAsJsonObject();
                edits.add(new ConditionalEdit(new TemplateNbtPatcher.Edit(e.get("path").getAsString(), e.get("operation").getAsString(), e.get("expectedId").getAsString()),
                        e.get("repair").getAsString(), e.has("unlessItem") ? e.get("unlessItem").getAsString() : null,
                        e.has("unlessEntity") ? e.get("unlessEntity").getAsString() : null));
            });
            if (rules.put(json.get("resource").getAsString(), new Rule(json.get("mod").getAsString(), json.get("sha256").getAsString(), List.copyOf(edits))) != null)
                throw new IllegalArgumentException("Duplicate template rule");
        });
    }
    public boolean candidate(String resource) { return rules.containsKey(resource); }

    public Result repair(String resource, byte[] original, Predicate<String> modLoaded, Predicate<String> enabled,
                         Predicate<String> itemPresent, Predicate<String> entityPresent) throws IOException {
        var rule = rules.get(resource);
        if (rule == null || !modLoaded.test(rule.mod) || !DataRepairEngine.sha256(original).equals(rule.hash))
            return new Result(original, 0, Set.of());
        var selected = new ArrayList<TemplateNbtPatcher.Edit>(); var ids = new HashSet<String>();
        for (var edit : rule.edits) {
            if (!enabled.test(edit.repair) || edit.item != null && itemPresent.test(edit.item)
                    || edit.entity != null && entityPresent.test(edit.entity)) continue;
            selected.add(edit.edit); ids.add(edit.repair);
        }
        var result = TemplateNbtPatcher.patch(original, selected);
        return new Result(result.bytes(), result.changes(), result.changes() == 0 ? Set.of() : Set.copyOf(ids));
    }
}
