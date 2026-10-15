package com.sanhiruzu.packrepairs.mixin;

import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ModifiableBiomeInfo.class, remap = false)
public interface ModifiableBiomeInfoAccessor {
    @Accessor("modifiedBiomeInfo")
    void packrepairs$setModifiedBiomeInfo(ModifiableBiomeInfo.BiomeInfo value);
}
