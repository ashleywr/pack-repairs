package com.sanhiruzu.packrepairs.mixin;

import java.util.ArrayList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

/** Two icon arguments evaluate their providers before entering Builder.icon; defer those call sites too. */
public final class CreateAdvancementIconsExtension implements IExtension {
    static final String TARGET = "com/simibubi/create/foundation/advancement/AllAdvancements";
    static final String BUILDER = "com/simibubi/create/foundation/advancement/CreateAdvancement$Builder";
    static final String HELPER = "com/sanhiruzu/packrepairs/DeferredAdvancementSupport";
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {
        if (repair(context.getClassNode())) LoggerFactory.getLogger("Pack Repairs").info("Applied create.deferred_advancement_items to package and chocolate icon arguments");
    }
    static boolean repair(ClassNode node) {
        if (!TARGET.equals(node.name)) return false;
        MethodInsnNode box = null, boxIcon = null, fluidIcon = null; FieldInsnNode fluid = null;
        var fluidChain = new ArrayList<AbstractInsnNode>();
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("com/simibubi/create/content/logistics/box/PackageStyles")
                    && call.name.equals("getDefaultBox") && call.desc.equals("()Lnet/minecraft/world/item/ItemStack;")) {
                if (box != null) return false; box = call;
                if (next(call) instanceof MethodInsnNode icon && icon.owner.equals(BUILDER) && icon.name.equals("icon") && icon.desc.startsWith("(Lnet/minecraft/world/item/ItemStack;)")) boxIcon = icon;
            }
            if (instruction instanceof FieldInsnNode field && field.owner.equals("com/simibubi/create/AllFluids") && field.name.equals("CHOCOLATE")) {
                if (fluid != null) return false; fluid = field;
                var get = next(field); var cast = next(get); var bucket = next(cast); var icon = next(bucket);
                if (!(get instanceof MethodInsnNode g) || !g.name.equals("get") || !g.owner.equals("com/tterrag/registrate/util/entry/FluidEntry")
                        || !(cast instanceof TypeInsnNode c) || !c.desc.equals("net/neoforged/neoforge/fluids/BaseFlowingFluid$Flowing")
                        || !(bucket instanceof MethodInsnNode b) || !b.name.equals("getBucket")
                        || !(icon instanceof MethodInsnNode i) || !i.owner.equals(BUILDER) || !i.name.equals("icon")) return false;
                fluidChain.add(get); fluidChain.add(cast); fluidChain.add(bucket); fluidIcon = i;
            }
        }
        if (box == null || boxIcon == null || fluid == null || fluidIcon == null) return false;
        box.owner = HELPER; box.name = "packageIcon"; box.desc = "()Ljava/util/function/Function;";
        boxIcon.desc = "(Ljava/util/function/Function;)L" + BUILDER + ";";
        for (var method : node.methods) if (method.instructions.contains(fluid)) {
            method.instructions.insertBefore(fluid, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "chocolateIcon", "()Ljava/util/function/Function;", false));
            method.instructions.remove(fluid); for (var instruction : fluidChain) method.instructions.remove(instruction); break;
        }
        fluidIcon.desc = "(Ljava/util/function/Function;)L" + BUILDER + ";";
        return true;
    }
    private static AbstractInsnNode next(AbstractInsnNode node) {
        if (node == null) return null;
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
}
