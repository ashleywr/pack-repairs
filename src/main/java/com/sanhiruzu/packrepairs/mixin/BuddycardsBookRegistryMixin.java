package com.sanhiruzu.packrepairs.mixin;

import com.sanhiruzu.packrepairs.BuddycardsRulebookRepair;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "vazkii.patchouli.common.book.BookRegistry", remap = false)
public abstract class BuddycardsBookRegistryMixin {
    @ModifyVariable(method = "loadBook", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private InputStream packrepairs$book(InputStream stream, @Coerce Object container, ResourceLocation id,
                                        InputStream original, boolean external) throws IOException {
        return BuddycardsRulebookRepair.declaration(id.toString(), external, stream);
    }
}
