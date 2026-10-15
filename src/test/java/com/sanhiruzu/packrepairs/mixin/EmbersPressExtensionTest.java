package com.sanhiruzu.packrepairs.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

class EmbersPressExtensionTest {
    @Test void preservesExistingDeclaredGetters() {
        var target = new ClassNode();
        var existing = new MethodNode(1, "getWorld", "()Lnet/minecraft/world/level/Level;", null, null);
        target.methods.add(existing);
        assertNull(EmbersPressExtension.bridge(target, existing.name, existing.desc));
        assertSame(existing, target.methods.getFirst());
        assertEquals(1, target.methods.size());
    }
}
