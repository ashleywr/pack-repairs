package com.sanhiruzu.packrepairs.mixin;

import com.sanhiruzu.packrepairs.RepairRuntime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wrap the selected resource, preserving pack priority, metadata, filters and native parsing. */
@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {
    @Shadow @Final private PackType type;

    @Inject(method = "getResource", at = @At("RETURN"), cancellable = true)
    private void packrepairs$resource(ResourceLocation id, CallbackInfoReturnable<Optional<Resource>> result) {
        if (type == PackType.SERVER_DATA && RepairRuntime.candidate(id)) result.setReturnValue(result.getReturnValue().map(value -> RepairRuntime.wrap(id, value)));
    }

    @Inject(method = "getResourceStack", at = @At("RETURN"), cancellable = true)
    private void packrepairs$stack(ResourceLocation id, CallbackInfoReturnable<List<Resource>> result) {
        if (type == PackType.SERVER_DATA && RepairRuntime.candidate(id)) {
            var wrapped = new ArrayList<Resource>();
            result.getReturnValue().forEach(value -> wrapped.add(RepairRuntime.wrap(id, value)));
            result.setReturnValue(wrapped);
        }
    }

    @Inject(method = "listResources", at = @At("RETURN"), cancellable = true)
    private void packrepairs$list(String path, Predicate<ResourceLocation> filter, CallbackInfoReturnable<Map<ResourceLocation, Resource>> result) {
        if (type != PackType.SERVER_DATA) return;
        if (result.getReturnValue().keySet().stream().noneMatch(RepairRuntime::candidate)) return;
        var wrapped = new LinkedHashMap<ResourceLocation, Resource>();
        result.getReturnValue().forEach((id, resource) -> wrapped.put(id, RepairRuntime.wrap(id, resource)));
        result.setReturnValue(wrapped);
    }

    @Inject(method = "listResourceStacks", at = @At("RETURN"), cancellable = true)
    private void packrepairs$lists(String path, Predicate<ResourceLocation> filter, CallbackInfoReturnable<Map<ResourceLocation, List<Resource>>> result) {
        if (type != PackType.SERVER_DATA) return;
        if (result.getReturnValue().keySet().stream().noneMatch(RepairRuntime::candidate)) return;
        var wrapped = new LinkedHashMap<ResourceLocation, List<Resource>>();
        result.getReturnValue().forEach((id, stack) -> {
            if (!RepairRuntime.candidate(id)) wrapped.put(id, stack);
            else {
                var repaired = new ArrayList<Resource>();
                stack.forEach(value -> repaired.add(RepairRuntime.wrap(id, value)));
                wrapped.put(id, repaired);
            }
        });
        result.setReturnValue(wrapped);
    }
}
