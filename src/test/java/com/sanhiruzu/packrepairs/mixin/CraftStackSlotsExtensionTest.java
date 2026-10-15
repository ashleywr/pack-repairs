package com.sanhiruzu.packrepairs.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class CraftStackSlotsExtensionTest {
    @Test void codecAndValidationChangeTogetherAndOnlyOnce() {
        var node = original();
        assertTrue(CraftStackSlotsExtension.repair(node));
        assertFalse(CraftStackSlotsExtension.repair(node));
        for (var method : node.methods) {
            var call = (MethodInsnNode) method.instructions.getFirst();
            assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
            assertEquals(CraftStackSlotsExtension.HELPER, call.owner);
        }
        assertEquals("(Lnet/minecraft/network/FriendlyByteBuf;)I", ((MethodInsnNode) node.methods.getFirst().instructions.getFirst()).desc);
    }

    @Test void driftAndOtherTargetsAreUntouched() {
        var node = original(); node.methods.removeLast();
        assertFalse(CraftStackSlotsExtension.repair(node));
        assertEquals("readByte", ((MethodInsnNode) node.methods.getFirst().instructions.getFirst()).name);
        node = original(); node.name = "custom/Packet";
        assertFalse(CraftStackSlotsExtension.repair(node));
    }

    private static ClassNode original() {
        var node = new ClassNode(); node.name = CraftStackSlotsExtension.TARGET;
        call(node, "decode", "net/minecraft/network/FriendlyByteBuf", "readByte", "()B");
        call(node, "encode", "net/minecraft/network/FriendlyByteBuf", "writeByte", "(I)Lnet/minecraft/network/FriendlyByteBuf;");
        call(node, "handle", "net/minecraft/world/inventory/AbstractContainerMenu", "isValidSlotIndex", "(I)Z");
        return node;
    }
    private static void call(ClassNode node, String methodName, String owner, String name, String descriptor) {
        var method = new MethodNode(); method.name = methodName;
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, false));
        node.methods.add(method);
    }
}
