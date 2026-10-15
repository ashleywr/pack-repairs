package com.sanhiruzu.packrepairs.mixin;

import java.lang.reflect.Method;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

/** Supplies declared forwarding methods before Embers validates its inherited-method shadows. */
public final class EmbersPressExtension implements IExtension {
    static final String TARGET = "com/simibubi/create/content/kinetics/press/PressingBehaviour";
    static final String PARENT = "com/simibubi/create/content/kinetics/belt/behaviour/BeltProcessingBehaviour";
    private final Method addMethod;

    public EmbersPressExtension() throws ReflectiveOperationException {
        // Mixin's public extension SPI exposes ClassInfo but its method-registration hook is package-private.
        // Resolve it before enabling the extension; an unsupported Mixin version leaves upstream code alone.
        addMethod = ClassInfo.class.getDeclaredMethod("addMethod", MethodNode.class);
        addMethod.setAccessible(true);
    }

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }

    @Override public void preApply(ITargetClassContext context) {
        var node = context.getClassNode();
        if (!TARGET.equals(node.name) || !PARENT.equals(node.superName)) return;
        try {
            for (String[] method : new String[][] {
                    {"getWorld", "()Lnet/minecraft/world/level/Level;"},
                    {"getPos", "()Lnet/minecraft/core/BlockPos;"}}) {
                var bridge = bridge(node, method[0], method[1]);
                if (bridge != null) {
                    addMethod.invoke(context.getClassInfo(), bridge);
                    node.methods.add(bridge);
                }
            }
            LoggerFactory.getLogger("Pack Repairs").info("Applied embers.create_press_inherited_methods to Create PressingBehaviour");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not register the verified Embers/Create forwarding methods", exception);
        }
    }

    static MethodNode bridge(ClassNode node, String name, String descriptor) {
        if (node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor))) return null;
        var method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, name, descriptor, false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    @Override public void postApply(ITargetClassContext context) {}
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
}
