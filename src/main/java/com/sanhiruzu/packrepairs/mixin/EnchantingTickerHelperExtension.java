package com.sanhiruzu.packrepairs.mixin;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

/** Keep Rechanted's private helper from hiding BaseEntityBlock's inherited protected helper. */
public final class EnchantingTickerHelperExtension implements IExtension {
    static final String TARGET = "net/minecraft/world/level/block/EnchantingTableBlock";
    static final String SOURCE = "net.tagtart.rechanted.mixins.EnchantmentTableBlockMixin";
    static final String ORIGINAL = "createTickerHelper";
    static final String RENAMED = "packrepairs$rechantedTickerHelper";
    static final String TYPE = "Lnet/minecraft/world/level/block/entity/BlockEntityType;";
    static final String TICKER = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;";
    static final String DESCRIPTOR = "(" + TYPE + TYPE + TICKER + ")" + TICKER;

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {
        if (repair(context.getClassNode())) LoggerFactory.getLogger("Pack Repairs").info(
                "Applied rechanted.inherited_ticker_helper: namespaced the verified private helper; inherited ticker lookup remains available");
    }

    static boolean repair(ClassNode node) {
        if (!TARGET.equals(node.name)) return false;
        var helpers = node.methods.stream().filter(m -> m.name.equals(ORIGINAL) && m.desc.equals(DESCRIPTOR)).toList();
        if (helpers.size() != 1 || node.methods.stream().anyMatch(m -> m.name.equals(RENAMED))) return false;
        var helper = helpers.getFirst();
        if ((helper.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)
                || !mergedFromRechanted(helper.visibleAnnotations) && !mergedFromRechanted(helper.invisibleAnnotations)) return false;
        var instructions = new ArrayList<AbstractInsnNode>();
        for (var instruction : helper.instructions) if (instruction.getOpcode() >= 0) instructions.add(instruction);
        if (!instructions.stream().map(AbstractInsnNode::getOpcode).toList().equals(List.of(
                Opcodes.ALOAD, Opcodes.ALOAD, Opcodes.IF_ACMPNE, Opcodes.ALOAD, Opcodes.GOTO, Opcodes.ACONST_NULL, Opcodes.ARETURN))
                || !(instructions.get(0) instanceof VarInsnNode first) || first.var != 1
                || !(instructions.get(1) instanceof VarInsnNode second) || second.var != 0
                || !(instructions.get(3) instanceof VarInsnNode ticker) || ticker.var != 2) return false;
        var calls = new ArrayList<MethodInsnNode>();
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(TARGET)
                    && call.name.equals(ORIGINAL) && call.desc.equals(DESCRIPTOR)) {
                if (call.getOpcode() != Opcodes.INVOKESTATIC || call.itf) return false;
                calls.add(call);
            }
        }
        helper.name = RENAMED;
        for (var call : calls) call.name = RENAMED;
        return true;
    }

    private static boolean mergedFromRechanted(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (var annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2)
                if (annotation.values.get(i).equals("mixin") && SOURCE.equals(annotation.values.get(i + 1))) return true;
        }
        return false;
    }

    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
}
