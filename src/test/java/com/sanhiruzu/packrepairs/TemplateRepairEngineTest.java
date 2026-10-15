package com.sanhiruzu.packrepairs;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateRepairEngineTest {
    @Test void keepsIndexedPositionsOmitsSparseEmptySlotsAndPreservesRawGeometryBits() throws Exception {
        byte[] original = inventory(false), expected = inventory(true);
        var edits = List.of(new TemplateNbtPatcher.Edit("/items/0", "empty", "minecraft:air"), new TemplateNbtPatcher.Edit("/items/1", "empty", "minecraft:air"));
        var result = TemplateNbtPatcher.patch(original, edits);
        assertEquals(2, result.changes()); assertArrayEquals(unzip(expected), unzip(result.bytes()));
    }

    @Test void integrationPresenceRestoresOriginalAndSignatureOrDisableSkipsEdits() throws Exception {
        byte[] original = inventory(false);
        var catalog = JsonParser.parseString("[{\"resource\":\"fixture:structure/test.nbt\",\"mod\":\"fixture\",\"sha256\":\"" + DataRepairEngine.sha256(original)
                + "\",\"edits\":[{\"path\":\"/items/0\",\"operation\":\"empty\",\"expectedId\":\"minecraft:air\",\"unlessItem\":\"missing:optional\",\"repair\":\"optional\"}]}]").getAsJsonArray();
        var engine = new TemplateRepairEngine(catalog); String id = "fixture:structure/test.nbt";
        assertSame(original, engine.repair(id, original, x -> true, x -> true, x -> true, x -> false).bytes());
        assertSame(original, engine.repair(id, original, x -> false, x -> true, x -> false, x -> false).bytes());
        assertSame(original, engine.repair(id, original, x -> true, x -> false, x -> false, x -> false).bytes());
        byte[] custom = original.clone(); custom[4] ^= 1;
        assertSame(custom, engine.repair(id, custom, x -> true, x -> true, x -> false, x -> false).bytes());
        var fixed = engine.repair(id, original, x -> true, x -> true, x -> false, x -> false);
        assertEquals(1, fixed.changes());
        assertSame(fixed.bytes(), engine.repair(id, fixed.bytes(), x -> true, x -> true, x -> false, x -> false).bytes());
    }

    @Test void removingAnUnavailableEntityPreservesNeighborsAndListAlignment() throws Exception {
        byte[] original = inventory(false);
        var result = TemplateNbtPatcher.patch(original, List.of(new TemplateNbtPatcher.Edit("/items/1", "remove", "minecraft:air")));
        var input = new DataInputStream(new ByteArrayInputStream(unzip(result.bytes())));
        assertEquals(10,input.readByte()); input.readUTF();
        assertEquals(9,input.readByte()); assertEquals("items",input.readUTF()); assertEquals(10,input.readByte()); assertEquals(2,input.readInt());
        assertEquals(1,result.changes());
    }

    @Test void wrongTargetAndTruncatedDataAreRejected() throws Exception {
        byte[] original = inventory(false);
        assertThrows(IOException.class, () -> TemplateNbtPatcher.patch(original, List.of(new TemplateNbtPatcher.Edit("/items/2", "empty", "minecraft:air"))));
        assertThrows(IOException.class, () -> TemplateNbtPatcher.patch(Arrays.copyOf(original,original.length-5), List.of(new TemplateNbtPatcher.Edit("/items/0", "empty", "minecraft:air"))));
        assertSame(original, TemplateNbtPatcher.patch(original,List.of()).bytes());
    }

    private static byte[] inventory(boolean fixed) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);out.writeUTF("fixture");
            out.writeByte(9);out.writeUTF("items");out.writeByte(10);out.writeInt(fixed ? 2 : 3);
            if (fixed) out.writeByte(0); else item(out,"minecraft:air",null);
            if (!fixed) item(out,"minecraft:air",1);
            item(out,"minecraft:diamond",2);
            out.writeByte(11);out.writeUTF("geometry");out.writeInt(3);out.writeInt(-100);out.writeInt(19);out.writeInt(222);
            out.writeByte(5);out.writeUTF("nanBits");out.writeInt(0x7fc00001);
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }
    private static void item(DataOutputStream out,String id,Integer slot) throws IOException {
        out.writeByte(8);out.writeUTF("id");out.writeUTF(id);
        out.writeByte(1);out.writeUTF("Count");out.writeByte(1);
        if (slot!=null) {out.writeByte(1);out.writeUTF("Slot");out.writeByte(slot);}
        out.writeByte(0);
    }
    private static byte[] unzip(byte[] bytes) throws IOException { try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))) {return input.readAllBytes();} }
}
