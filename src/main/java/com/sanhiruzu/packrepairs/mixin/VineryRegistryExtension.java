package com.sanhiruzu.packrepairs.mixin;

import java.util.Map;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

/** Relocates verified references after Vinery 1.5.5 moved its registry, grape type and bush classes. */
public final class VineryRegistryExtension implements IExtension {
    static final String OLD = "net/satisfy/vinery/core/registry/RecipeTypesRegistry";
    static final String NEW = "net/satisfy/vinery/core/registry/RecipeTypeRegistry";
    static final String SUPPLIER = "Ldev/architectury/registry/registries/RegistrySupplier;";
    static final Map<String, String> RELOCATIONS = Map.of(
            OLD, NEW,
            "net/satisfy/vinery/core/util/GrapeType", "net/satisfy/vinery/core/wine/GrapeType",
            "net/satisfy/vinery/core/block/GrapeBush", "net/satisfy/vinery/core/block/GrapeBushBlock");
    static final Map<String, String> TARGET_HASHES = Map.of(
            "steve_gall/minecolonies_compatibility/module/common/lets_do_vinery/GrapeFruit",
            "7e2ab67e6797224cfe1cbe1a7afd6a525f3add4605b6c39b3991d2bbee152c8d",
            "steve_gall/minecolonies_compatibility/module/common/lets_do_vinery/LetsDoVineryModule",
            "ffd2dc1a28a6de17405f252babbdbb34cc1583a1cce3666131bfbf8534647242",
            "steve_gall/minecolonies_compatibility/module/common/lets_do_vinery/init/ModuleCraftingTypes",
            "29e3204fe5468244e0b9bc47ca2c3c2f2cbe4f7d9ca957197d93b9a02792410a",
            "steve_gall/minecolonies_compatibility/module/common/lets_do_vinery/menu/ApplePressFermentingTeachMenu$1",
            "80e59cbe9f609e7f1399b226c4b38404e6bd86f87d52fa330357fa1960213722",
            "steve_gall/minecolonies_compatibility/module/common/lets_do_vinery/menu/ApplePressMashingTeachMenu$1",
            "bf634bd9baafe641755a11a60068ec7875445a43bdcc9c70ca169829e097632e");

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }

    @Override public void preApply(ITargetClassContext context) {
        int count = repair(context.getClassNode());
        if (count > 0) LoggerFactory.getLogger("Pack Repairs").info(
                "Applied minecolonies_compatibility.vinery_api_relocations to {} ({} references)",
                context.getClassNode().name, count);
    }

    static int repair(ClassNode target) {
        if (!TARGET_HASHES.containsKey(target.name)) return 0;
        int[] count = {0};
        var mapped = new ClassNode();
        target.accept(new ClassRemapper(mapped, new Remapper() {
            @Override public String map(String name) {
                String replacement = RELOCATIONS.get(name);
                if (replacement == null) return name;
                count[0]++;
                return replacement;
            }
        }));
        if (count[0] == 0) return 0;
        // Class names, hierarchy and class annotations do not contain these aliases.
        // Replace members together so constructor signatures, field types and all
        // matching call sites remain consistent. Preserve Mixin's target node identity.
        target.fields = mapped.fields;
        target.methods = mapped.methods;
        return count[0];
    }

    @Override public void postApply(ITargetClassContext context) {}
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
}
