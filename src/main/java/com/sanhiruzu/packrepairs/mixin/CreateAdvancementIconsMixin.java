package com.sanhiruzu.packrepairs.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.advancement.AllAdvancements", remap = false)
public abstract class CreateAdvancementIconsMixin {}
