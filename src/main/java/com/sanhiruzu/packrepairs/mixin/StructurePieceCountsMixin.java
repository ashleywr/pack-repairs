package com.sanhiruzu.packrepairs.mixin;

import com.sanhiruzu.packrepairs.StructureCountMerge;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.telepathicgrunt.repurposedstructures.misc.structurepiececounter.StructurePieceCountsManager", remap = false)
public abstract class StructurePieceCountsMixin {
    @Redirect(method = "lambda$apply$0", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private Object packrepairs$mergeContributors(Map<Object, List<Object>> counts, Object structure, Object incoming) {
        @SuppressWarnings("unchecked") var pieces = (List<Object>) incoming;
        return StructureCountMerge.put(counts, structure, pieces);
    }
}
