package com.sanhiruzu.packrepairs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.blay09.mods.craftingtweaks.network.CraftStackMessage", remap = false)
public abstract class CraftStackSlotsMixin {}
