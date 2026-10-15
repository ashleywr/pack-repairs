package com.sanhiruzu.packrepairs.mixin;

import com.sanhiruzu.packrepairs.DeferredAdvancementCriteria;
import com.sanhiruzu.packrepairs.DeferredAdvancementSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.advancement.CreateAdvancement$Builder", remap = false)
public abstract class CreateAdvancementBuilderMixin implements DeferredAdvancementCriteria {
    @Shadow private Function<HolderLookup.Provider, ItemStack> func;
    @Shadow private ItemStack icon;
    @Shadow private boolean externalTrigger;
    @Shadow private int keyIndex;
    @Unique private List<Map.Entry<Integer, Supplier<Criterion<?>>>> packrepairs$pending;

    @Inject(method = "icon(Lcom/tterrag/registrate/util/entry/ItemProviderEntry;)Lcom/simibubi/create/foundation/advancement/CreateAdvancement$Builder;", at = @At("HEAD"), cancellable = true)
    private void packrepairs$entryIcon(@Coerce Object item, CallbackInfoReturnable<Object> result) {
        func = registry -> DeferredAdvancementSupport.stack(item); result.setReturnValue(this);
    }
    @Inject(method = "icon(Lnet/minecraft/world/level/ItemLike;)Lcom/simibubi/create/foundation/advancement/CreateAdvancement$Builder;", at = @At("HEAD"), cancellable = true)
    private void packrepairs$itemIcon(ItemLike item, CallbackInfoReturnable<Object> result) {
        func = registry -> new ItemStack(item); result.setReturnValue(this);
    }
    @Inject(method = "whenIconCollected", at = @At("HEAD"), cancellable = true)
    private void packrepairs$iconCriterion(CallbackInfoReturnable<Object> result) {
        packrepairs$defer(() -> InventoryChangeTrigger.TriggerInstance.hasItems(icon.getItem())); result.setReturnValue(this);
    }
    @Inject(method = "whenItemCollected(Lcom/tterrag/registrate/util/entry/ItemProviderEntry;)Lcom/simibubi/create/foundation/advancement/CreateAdvancement$Builder;", at = @At("HEAD"), cancellable = true)
    private void packrepairs$itemCriterion(@Coerce Object item, CallbackInfoReturnable<Object> result) {
        packrepairs$defer(() -> InventoryChangeTrigger.TriggerInstance.hasItems(DeferredAdvancementSupport.stack(item).getItem())); result.setReturnValue(this);
    }
    @Unique private void packrepairs$defer(Supplier<Criterion<?>> criterion) {
        if (packrepairs$pending == null) packrepairs$pending = new ArrayList<>();
        packrepairs$pending.add(Map.entry(keyIndex++, criterion)); externalTrigger = true;
    }
    @Override public void packrepairs$flush(Advancement.Builder builder) {
        if (packrepairs$pending == null) return;
        for (var entry : packrepairs$pending) builder.addCriterion(Integer.toString(entry.getKey()), entry.getValue().get());
        packrepairs$pending.clear();
    }
}
