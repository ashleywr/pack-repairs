package com.sanhiruzu.packrepairs.mixin;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class EnchantingTickerHelperExtensionTest {
    @Test void namespacesOnlyTheMergedPrivateHelperAndKeepsItsBody() {
        var node = target(); var helper = node.methods.getFirst(); var body = helper.instructions;
        var method = new MethodNode(Opcodes.ACC_PUBLIC, "getTicker", "()V", null, null);
        var call = new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, helper.name, helper.desc, false);
        var inherited = new MethodInsnNode(Opcodes.INVOKESTATIC, "custom/EnchantingTableSubclass", helper.name, helper.desc, false);
        method.instructions.add(call); method.instructions.add(inherited); node.methods.add(method);
        assertTrue(EnchantingTickerHelperExtension.repair(node));
        assertEquals(EnchantingTickerHelperExtension.RENAMED, helper.name);
        assertEquals(helper.name, call.name);
        assertEquals(EnchantingTickerHelperExtension.ORIGINAL, inherited.name);
        assertSame(body, helper.instructions);
        assertFalse(EnchantingTickerHelperExtension.repair(node));
    }

    @Test void leavesUnverifiedOriginsPublicHelpersAndChangedBodiesAlone() {
        var otherOrigin = target(); otherOrigin.methods.getFirst().visibleAnnotations.getFirst().values.set(1, "custom.Mixin");
        assertFalse(EnchantingTickerHelperExtension.repair(otherOrigin));
        var publicHelper = target(); publicHelper.methods.getFirst().access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        assertFalse(EnchantingTickerHelperExtension.repair(publicHelper));
        var changedBody = target(); changedBody.methods.getFirst().instructions.insert(new InsnNode(Opcodes.NOP));
        assertFalse(EnchantingTickerHelperExtension.repair(changedBody));
        assertEquals(EnchantingTickerHelperExtension.ORIGINAL, changedBody.methods.getFirst().name);
        var unrelated = target(); unrelated.name = "custom/EnchantingBlock";
        assertFalse(EnchantingTickerHelperExtension.repair(unrelated));
    }

    @Test void validatesAllCallSitesBeforeChangingTheHelper() {
        var node = target(); var helper = node.methods.getFirst();
        var invalid = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, helper.name, helper.desc, false);
        var method = new MethodNode(Opcodes.ACC_PUBLIC, "getTicker", "()V", null, null);
        method.instructions.add(invalid); node.methods.add(method);
        assertFalse(EnchantingTickerHelperExtension.repair(node));
        assertEquals(EnchantingTickerHelperExtension.ORIGINAL, helper.name);
        assertEquals(helper.name, invalid.name);
    }

    private static ClassNode target() {
        var node = new ClassNode(); node.name = EnchantingTickerHelperExtension.TARGET;
        var helper = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                EnchantingTickerHelperExtension.ORIGINAL, EnchantingTickerHelperExtension.DESCRIPTOR, null, null);
        var merged = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        merged.values = new ArrayList<>(List.of("mixin", EnchantingTickerHelperExtension.SOURCE));
        helper.visibleAnnotations = new ArrayList<>(List.of(merged));
        var mismatch = new LabelNode(); var end = new LabelNode();
        helper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        helper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        helper.instructions.add(new JumpInsnNode(Opcodes.IF_ACMPNE, mismatch));
        helper.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        helper.instructions.add(new JumpInsnNode(Opcodes.GOTO, end));
        helper.instructions.add(mismatch); helper.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        helper.instructions.add(end); helper.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(helper); return node;
    }
}
