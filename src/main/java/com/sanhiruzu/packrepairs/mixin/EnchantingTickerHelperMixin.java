package com.sanhiruzu.packrepairs.mixin;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.world.level.block.EnchantingTableBlock", remap = false)
public abstract class EnchantingTickerHelperMixin {}
