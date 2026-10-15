package com.sanhiruzu.packrepairs.mixin;

import com.sanhiruzu.packrepairs.DeferredAdvancementSupport;
import java.util.function.Consumer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.advancement.CreateAdvancement", remap = false)
public abstract class CreateAdvancementSaveMixin {
    @Inject(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/Advancement$Builder;display(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/advancements/AdvancementType;ZZZ)Lnet/minecraft/advancements/Advancement$Builder;"))
    private void packrepairs$finishCriteria(Consumer<AdvancementHolder> output, HolderLookup.Provider registries, CallbackInfo callback) {
        DeferredAdvancementSupport.flush(this);
    }
}
