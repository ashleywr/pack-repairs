package com.sanhiruzu.packrepairs;

import java.lang.reflect.InvocationTargetException;
import net.minecraft.advancements.Advancement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.material.Fluid;
import java.util.function.Function;

/** Reflection remains behind verified optional mixins; neither optional class is linked statically. */
public final class DeferredAdvancementSupport {
    private DeferredAdvancementSupport() {}
    public static Function<HolderLookup.Provider, ItemStack> packageIcon() {
        return registry -> {
            try { return (ItemStack)Class.forName("com.simibubi.create.content.logistics.box.PackageStyles").getMethod("getDefaultBox").invoke(null); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot resolve deferred package icon", error); }
        };
    }
    public static Function<HolderLookup.Provider, ItemStack> chocolateIcon() {
        return registry -> {
            try {
                Object entry = Class.forName("com.simibubi.create.AllFluids").getField("CHOCOLATE").get(null);
                var fluid = (Fluid)entry.getClass().getMethod("get").invoke(entry);
                return new ItemStack(fluid.getBucket());
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot resolve deferred chocolate icon", error); }
        };
    }
    public static ItemStack stack(Object entry) {
        try { return (ItemStack)entry.getClass().getMethod("asStack").invoke(entry); }
        catch (InvocationTargetException error) { throw new IllegalStateException("Cannot resolve deferred Create icon", error.getCause()); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Verified Create icon API changed", error); }
    }
    public static void flush(Object advancement) {
        try {
            var type = advancement.getClass();
            var createBuilder = type.getDeclaredField("createBuilder"); createBuilder.setAccessible(true);
            var mcBuilder = type.getDeclaredField("mcBuilder"); mcBuilder.setAccessible(true);
            ((DeferredAdvancementCriteria)createBuilder.get(advancement)).packrepairs$flush((Advancement.Builder)mcBuilder.get(advancement));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Verified Create advancement layout changed", error); }
    }
}
