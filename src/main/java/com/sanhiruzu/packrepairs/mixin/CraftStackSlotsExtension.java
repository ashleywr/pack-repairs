package com.sanhiruzu.packrepairs.mixin;

import java.util.ArrayList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

public final class CraftStackSlotsExtension implements IExtension {
    static final String TARGET = "net/blay09/mods/craftingtweaks/network/CraftStackMessage";
    static final String HELPER = "com/sanhiruzu/packrepairs/CraftStackSlots";
    private static final String BUF = "net/minecraft/network/FriendlyByteBuf";
    private static final String MENU = "net/minecraft/world/inventory/AbstractContainerMenu";

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {
        if (repair(context.getClassNode())) LoggerFactory.getLogger("Pack Repairs").info(
                "Applied craftingtweaks.full_range_stack_slots to verified packet codec and slot validation");
    }

    static boolean repair(ClassNode node) {
        if (!TARGET.equals(node.name)) return false;
        var reads = new ArrayList<MethodInsnNode>();
        var writes = new ArrayList<MethodInsnNode>();
        var checks = new ArrayList<MethodInsnNode>();
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (method.name.equals("decode") && call.owner.equals(BUF) && call.name.equals("readByte") && call.desc.equals("()B")) reads.add(call);
            if (method.name.equals("encode") && call.owner.equals(BUF) && call.name.equals("writeByte") && call.desc.equals("(I)L" + BUF + ";")) writes.add(call);
            if (method.name.equals("handle") && call.owner.equals(MENU) && call.name.equals("isValidSlotIndex") && call.desc.equals("(I)Z")) checks.add(call);
        }
        // Validate the complete shape before changing anything; upstream changes fail closed.
        if (reads.size() != 1 || writes.size() != 1 || checks.size() != 1) return false;
        redirect(reads.getFirst(), "read", "(L" + BUF + ";)I");
        redirect(writes.getFirst(), "write", "(L" + BUF + ";I)L" + BUF + ";");
        redirect(checks.getFirst(), "valid", "(L" + MENU + ";I)Z");
        return true;
    }

    private static void redirect(MethodInsnNode call, String name, String descriptor) {
        call.setOpcode(Opcodes.INVOKESTATIC); call.owner = HELPER;
        call.name = name; call.desc = descriptor; call.itf = false;
    }
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
}
