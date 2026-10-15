package com.sanhiruzu.packrepairs.mixin;

import com.sanhiruzu.packrepairs.DataRepairEngine;
import com.sanhiruzu.packrepairs.RepairConfig;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class RepairMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("CreateAdvancementBuilderMixin") || mixinClassName.endsWith("CreateAdvancementSaveMixin") || mixinClassName.endsWith("CreateAdvancementIconsMixin")) return createAdvancementsMatch();
        if (mixinClassName.endsWith("CraftStackSlotsMixin")) return craftStackMatches();
        if (mixinClassName.endsWith("BuddycardsBookRegistryMixin")) return com.sanhiruzu.packrepairs.BuddycardsRulebookRepair.supported();
        if (mixinClassName.endsWith("VineryRegistryMixin")) return vineryMatches();
        if (mixinClassName.endsWith("StructurePieceCountsMixin")) {
            boolean matching = RepairConfig.enabled("repurposed_structures.merge_piece_limits")
                    && matches("repurposed_structures",
                    "com/telepathicgrunt/repurposedstructures/misc/structurepiececounter/StructurePieceCountsManager.class",
                    "4f45d99a05f0daa7cbdddeeef9b8636d290d75ca8d757fbf1aa472e488a56324");
            if (matching) LoggerFactory.getLogger("Pack Repairs").info("Enabled repurposed_structures.merge_piece_limits for the verified class signature");
            return matching;
        }
        if (!mixinClassName.endsWith("EngineVehicleMixin")) return true;
        if (!RepairConfig.enabled("aeroforge.riverhawk_water_power")) return false;
        boolean matching = matches("aeroforge", "nl/smartstreamlabs/aeroforge/entity/RiverhawkAircraftEntity.class",
                "b0a240addf332d109a646c93b53adc6c2a70e4d58c72afe1763e5fb53bd1cac4")
                && matches("immersive_aircraft", "immersive_aircraft/entity/EngineVehicle.class",
                "f98ee7e8c198dc02e8d46d0ee2c229c65ecf6921ad4458b2017745b4f6281688");
        if (matching) LoggerFactory.getLogger("Pack Repairs").info("Enabled aeroforge.riverhawk_water_power for the verified original class signatures");
        return matching;
    }

    private boolean matches(String mod, String entry, String hash) {
        var list = FMLLoader.getLoadingModList();
        if (list == null) return false;
        var file = list.getModFileById(mod);
        if (file == null) return false;
        try { return DataRepairEngine.sha256(Files.readAllBytes(file.getFile().findResource(entry))).equals(hash); }
        catch (Exception ignored) { return false; }
    }

    @Override public void onLoad(String mixinPackage) {
        if (createAdvancementsMatch()) {
            var active = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (active instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions)
                if (extensions.getExtensions().stream().noneMatch(extension -> extension instanceof CreateAdvancementIconsExtension))
                    extensions.add(new CreateAdvancementIconsExtension());
        }
        if (craftStackMatches()) {
            var active = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (active instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                if (extensions.getExtensions().stream().noneMatch(extension -> extension instanceof CraftStackSlotsExtension))
                    extensions.add(new CraftStackSlotsExtension());
            }
        }
        if (vineryMatches()) {
            var active = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (active instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                if (extensions.getExtensions().stream().noneMatch(extension -> extension instanceof VineryRegistryExtension))
                    extensions.add(new VineryRegistryExtension());
                LoggerFactory.getLogger("Pack Repairs").info("Enabled minecolonies_compatibility.vinery_api_relocations for verified class signatures");
            }
        }
        if (!RepairConfig.enabled("embers.create_press_inherited_methods")) return;
        if (!matches("embers", "com/rekindled/embers/mixin/CreatePressDawnstoneAnvilMixin.class",
                "5a9f98aef3eae491b935ad738d04b06144867e388e97a1480dd58736bdd1f35e")
                || !matches("create", "com/simibubi/create/content/kinetics/press/PressingBehaviour.class",
                "7aab75b917d925de24f200ef0fa29a02d5e50f435f0fa203847b2d1fe7e7a567")
                || !matches("create", "com/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour.class",
                "2088a6dc96882f59f4df86430fe1c0f0bd277535ba282e341755f24060e162fc")) return;
        var transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (!(transformer instanceof IMixinTransformer mixin) || !(mixin.getExtensions() instanceof Extensions extensions)) {
            LoggerFactory.getLogger("Pack Repairs").warn("Embers/Create repair unavailable: Mixin extension registry is unsupported");
            return;
        }
        try {
            if (extensions.getExtensions().stream().noneMatch(extension -> extension instanceof EmbersPressExtension)) {
                extensions.add(new EmbersPressExtension());
                LoggerFactory.getLogger("Pack Repairs").info("Enabled embers.create_press_inherited_methods for verified class signatures");
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LoggerFactory.getLogger("Pack Repairs").warn("Embers/Create repair unavailable: cannot register compatibility extension", exception);
        }
    }
    private boolean createAdvancementsMatch() {
        boolean enabled = RepairConfig.enabled("create.deferred_advancement_items")
                && matches("create", "com/simibubi/create/foundation/advancement/CreateAdvancement.class", "737bad82201ee57f70d5b56c8f2c6af006a7988f7863f1dbd5741a0675fc938e")
                && matches("create", "com/simibubi/create/foundation/advancement/CreateAdvancement$Builder.class", "cab866261e2fbc5c7a731cdd95384ea87ff1104cb69a0571e3d8eb8ccf1f9d90")
                && matches("create", "com/simibubi/create/foundation/advancement/AllAdvancements.class", "35c05c688958a977f236062e1deeed4ef4458338a120e336b7cbe31fabe2b7f1");
        if (enabled) LoggerFactory.getLogger("Pack Repairs").info("Enabled create.deferred_advancement_items for verified class signatures");
        return enabled;
    }

    private boolean craftStackMatches() {
        return RepairConfig.enabled("craftingtweaks.full_range_stack_slots")
                && matches("craftingtweaks", CraftStackSlotsExtension.TARGET + ".class",
                "524353e8aa2aaab73f0d18b268b85bfdcc86d71a6386eb158822d01712d90185");
    }

    private boolean vineryMatches() {
        if (!RepairConfig.enabled("minecolonies_compatibility.vinery_api_relocations")
                || !matches("vinery", "net/satisfy/vinery/core/registry/RecipeTypeRegistry.class",
                "6e434dce3ece1c6c57729f4a7622527bfe951e93bd54910064c142af1ae9ba03")
                || !matches("vinery", "net/satisfy/vinery/core/wine/GrapeType.class",
                "27a3ca6b866fe848da122964e71ead1bbfb12027bbfc31dd8d4b2fc7755f7e97")
                || !matches("vinery", "net/satisfy/vinery/core/block/GrapeBushBlock.class",
                "039717e7e0798796c27a31af225edd5be148eb6e6170bc51cea6e151a4023934")
                || !matches("vinery", "net/satisfy/vinery/core/registry/GrapeTypeRegistry.class",
                "1af128828f91997439afd8f4892602acc6df83f691493b39495af23b22929e09")) return false;
        for (var entry : VineryRegistryExtension.TARGET_HASHES.entrySet()) {
            if (!matches("minecolonies_compatibility", entry.getKey() + ".class", entry.getValue())) return false;
        }
        var file = FMLLoader.getLoadingModList().getModFileById("vinery");
        return file != null && VineryRegistryExtension.RELOCATIONS.keySet().stream()
                .noneMatch(name -> Files.exists(file.getFile().findResource(name + ".class")));
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
