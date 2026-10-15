package com.sanhiruzu.packrepairs;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Retains the original one-byte format, with an optional high-byte extension for large menus. */
public final class CraftStackSlots {
    private CraftStackSlots() {}

    public static int read(FriendlyByteBuf buffer) {
        int low = buffer.readUnsignedByte();
        if (!buffer.isReadable()) return low;
        int high = buffer.readVarInt();
        if (high < 1 || high > (Integer.MAX_VALUE >>> 8) || buffer.isReadable())
            throw new IllegalArgumentException("Invalid extended craft-stack slot");
        return (high << 8) | low;
    }

    public static FriendlyByteBuf write(FriendlyByteBuf buffer, int slot) {
        if (slot < 0) throw new IllegalArgumentException("Negative craft-stack slot");
        buffer.writeByte(slot);
        if (slot > 255) buffer.writeVarInt(slot >>> 8);
        return buffer;
    }

    public static boolean valid(AbstractContainerMenu menu, int slot) {
        return slot >= 0 && menu.isValidSlotIndex(slot);
    }
}
