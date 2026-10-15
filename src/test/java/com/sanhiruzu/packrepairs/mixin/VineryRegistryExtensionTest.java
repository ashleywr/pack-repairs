package com.sanhiruzu.packrepairs.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class VineryRegistryExtensionTest {
    @Test void redirectsSupplierReadsWithoutChangingOtherOwners() {
        var node = target();
        var method = new MethodNode(Opcodes.ACC_PUBLIC, "read", "()V", null, null); node.methods.add(method);
        var mash = read("APPLE_PRESS_MASHING_RECIPE_TYPE");
        var ferment = read("APPLE_PRESS_FERMENTING_RECIPE_TYPE");
        var other = read("FERMENTATION_BARREL_RECIPE_TYPE");
        other.owner = "custom/UnrelatedRegistry";
        method.instructions.add(mash); method.instructions.add(ferment);
        method.instructions.add(other);
        assertEquals(2, VineryRegistryExtension.repair(node));
        var mapped = node.methods.getFirst().instructions;
        assertEquals(VineryRegistryExtension.NEW, ((FieldInsnNode) mapped.get(0)).owner);
        assertEquals(VineryRegistryExtension.NEW, ((FieldInsnNode) mapped.get(1)).owner);
        assertEquals(other.owner, ((FieldInsnNode) mapped.get(2)).owner);
        assertEquals(0, VineryRegistryExtension.repair(node));
    }

    @Test void keepsGrapeFieldConstructorCallerAndBushPropertyTypesConsistent() {
        var node = target();
        String oldType = "net/satisfy/vinery/core/util/GrapeType";
        String newType = VineryRegistryExtension.RELOCATIONS.get(oldType);
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "grape", "L" + oldType + ";", null, null));
        var method = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(L" + oldType + ";)V", null, null);
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, "grape", "L" + oldType + ";"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, "<init>", method.desc, false));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, oldType));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/satisfy/vinery/core/block/GrapeBush", "AGE", "Lnet/minecraft/world/level/block/state/properties/IntegerProperty;"));
        node.methods.add(method);
        assertTrue(VineryRegistryExtension.repair(node) > 0);
        var mapped = node.methods.getFirst();
        assertEquals("L" + newType + ";", node.fields.getFirst().desc);
        assertEquals("(L" + newType + ";)V", mapped.desc);
        assertEquals(node.fields.getFirst().desc, ((FieldInsnNode) mapped.instructions.get(0)).desc);
        assertEquals(mapped.desc, ((MethodInsnNode) mapped.instructions.get(1)).desc);
        assertEquals(newType, ((TypeInsnNode) mapped.instructions.get(2)).desc);
        assertEquals("net/satisfy/vinery/core/block/GrapeBushBlock", ((FieldInsnNode) mapped.instructions.get(3)).owner);
    }

    @Test void leavesOtherClassesAlone() {
        var node = target(); node.name = "custom/RegistryUser";
        var method = new MethodNode(Opcodes.ACC_PUBLIC, "read", "()V", null, null); node.methods.add(method);
        var field = read("APPLE_PRESS_MASHING_RECIPE_TYPE"); method.instructions.add(field);
        assertEquals(0, VineryRegistryExtension.repair(node));
        assertEquals(VineryRegistryExtension.OLD, field.owner);
        assertSame(method, node.methods.getFirst());
    }

    private static ClassNode target() {
        var node = new ClassNode(); node.version = Opcodes.V21; node.superName = "java/lang/Object";
        node.name = VineryRegistryExtension.TARGET_HASHES.keySet().iterator().next(); return node;
    }
    private static FieldInsnNode read(String name) {
        return new FieldInsnNode(Opcodes.GETSTATIC, VineryRegistryExtension.OLD, name, VineryRegistryExtension.SUPPLIER);
    }
}
