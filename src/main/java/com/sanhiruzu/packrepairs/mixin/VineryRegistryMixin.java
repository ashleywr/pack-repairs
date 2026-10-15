package com.sanhiruzu.packrepairs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Makes verified callers pass through Mixin's public transformation extension SPI. */
@Pseudo
@Mixin(targets = {
        "steve_gall.minecolonies_compatibility.module.common.lets_do_vinery.GrapeFruit",
        "steve_gall.minecolonies_compatibility.module.common.lets_do_vinery.LetsDoVineryModule",
        "steve_gall.minecolonies_compatibility.module.common.lets_do_vinery.init.ModuleCraftingTypes",
        "steve_gall.minecolonies_compatibility.module.common.lets_do_vinery.menu.ApplePressFermentingTeachMenu$1",
        "steve_gall.minecolonies_compatibility.module.common.lets_do_vinery.menu.ApplePressMashingTeachMenu$1"
}, remap = false)
public abstract class VineryRegistryMixin {}
