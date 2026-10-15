package com.sanhiruzu.packrepairs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.fml.ModList;

/** Corrects blank JSON lines in three verified templates without touching geometry or other NBT. */
public final class SignTemplateRepair {
    public static final String ID = "structures.blank_sign_json";
    private static final Map<String, String> HASHES = Map.of(
            "undergroundworlds:structure/tundra_house.nbt", "cf811eedf1c2a795cfbcf45f074c576eb83a1d7e800bb7bee7e9eea9fd7b776b",
            "undergroundworlds:structure/archives/entrance.nbt", "8dbadcf3ab32b88defd645779a11a56666e091a9d14acb7876ead555d78b1b2d",
            "witchery:structure/warpedcoven.nbt", "2640fa48f047742819c572c471a00086f41183897842356ecba9938381420ee8");
    private SignTemplateRepair() {}
    public static boolean candidate(String id) { return HASHES.containsKey(id); }
    public static byte[] repair(String id, byte[] original) throws IOException {
        String hash = HASHES.get(id);
        var mods = ModList.get();
        if (hash == null || mods == null || !mods.isLoaded(id.substring(0, id.indexOf(':')))
                || !RepairConfig.enabled(ID) || !DataRepairEngine.sha256(original).equals(hash)) return original;
        var root = NbtIo.readCompressed(new ByteArrayInputStream(original), NbtAccounter.unlimitedHeap());
        int changed = 0;
        for (var value : root.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag)value;
            if (!block.contains("nbt", Tag.TAG_COMPOUND)) continue;
            var data = block.getCompound("nbt");
            for (String side : new String[]{"front_text", "back_text"}) {
                if (!data.contains(side, Tag.TAG_COMPOUND)) continue;
                var text = data.getCompound(side);
                for (String key : new String[]{"messages", "filtered_messages"}) {
                    if (!text.contains(key, Tag.TAG_LIST)) continue;
                    ListTag lines = text.getList(key, Tag.TAG_STRING);
                    if (lines.size() != 4) continue;
                    for (int i = 0; i < 4; i++) if (lines.getString(i).isEmpty()) {
                        lines.set(i, StringTag.valueOf("{\"text\":\"\"}")); changed++;
                    }
                }
            }
        }
        if (changed == 0) return original;
        var output = new ByteArrayOutputStream(); NbtIo.writeCompressed(root, output);
        return output.toByteArray();
    }
}
