package com.sanhiruzu.packrepairs.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "immersive_aircraft.entity.EngineVehicle", remap = false)
public abstract class EngineVehicleMixin {
    @Inject(method = "worksUnderWater()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void packrepairs$riverhawk(CallbackInfoReturnable<Boolean> result) {
        if (getClass().getName().equals("nl.smartstreamlabs.aeroforge.entity.RiverhawkAircraftEntity")) {
            result.setReturnValue(!((Entity) (Object) this).isUnderWater());
        }
    }
}
