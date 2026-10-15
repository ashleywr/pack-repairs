package com.sanhiruzu.packrepairs;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Streams untouched subtrees, avoiding a second in-memory copy of large structure templates. */
public final class TemplateNbtPatcher {
    public record Edit(String path, String operation, String expectedId) {}
    public record Result(byte[] bytes, int changes) {}
    private static final class Node {
        final Map<String, Node> children = new HashMap<>();
        Edit edit;
    }
    private final DataInputStream input;
    private final byte[] buffer;
    private int changes;

    private TemplateNbtPatcher(InputStream input) { this(input, new byte[65536]); }
    private TemplateNbtPatcher(InputStream input, byte[] buffer) { this.input = new DataInputStream(input); this.buffer = buffer; }

    public static Result patch(byte[] original, List<Edit> edits) throws IOException {
        if (edits.isEmpty()) return new Result(original, 0);
        var tree = new Node();
        for (var edit : edits) {
            if (!edit.path().startsWith("/") || !(edit.operation().equals("empty") || edit.operation().equals("remove")))
                throw new IOException("Invalid template edit");
            var node = tree;
            for (String part : edit.path().substring(1).split("/")) node = node.children.computeIfAbsent(part, ignored -> new Node());
            if (node.edit != null) throw new IOException("Duplicate template edit");
            node.edit = edit;
        }
        var bytes = new ByteArrayOutputStream();
        int changed;
        try (var zip = new GZIPInputStream(new ByteArrayInputStream(original));
             var output = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(bytes), 65536))) {
            var parser = new TemplateNbtPatcher(new BufferedInputStream(zip, 65536));
            int type = parser.input.readUnsignedByte();
            if (type != 10) throw new IOException("Expected compound template root");
            output.writeByte(type); output.writeUTF(parser.input.readUTF());
            parser.copy(type, tree, output, 0);
            if (parser.input.read() != -1) throw new IOException("Trailing template NBT");
            changed = parser.changes;
        }
        return new Result(changed == 0 ? original : bytes.toByteArray(), changed);
    }

    private void copy(int type, Node node, DataOutputStream output, int depth) throws IOException {
        if (depth > 512) throw new IOException("Excessive NBT depth");
        switch (type) {
            case 1 -> transfer(1, output);
            case 2 -> transfer(2, output);
            case 3, 5 -> transfer(4, output);
            case 4, 6 -> transfer(8, output);
            case 7, 11, 12 -> {
                int count = input.readInt(); if (count < 0) throw new IOException("Negative NBT array");
                output.writeInt(count); transfer((long)count * (type == 7 ? 1 : type == 11 ? 4 : 8), output);
            }
            case 8 -> { int length = input.readUnsignedShort(); output.writeShort(length); transfer(length, output); }
            case 9 -> copyList(node, output, depth + 1);
            case 10 -> {
                for (int childType; (childType = input.readUnsignedByte()) != 0;) {
                    if (node == null) {
                        output.writeByte(childType); copy(8, null, output, depth + 1); copy(childType, null, output, depth + 1);
                        continue;
                    }
                    String name = input.readUTF(); var child = node.children.get(name);
                    if (child != null && child.edit != null && child.edit.operation().equals("remove")) {
                        copy(childType, null, new DataOutputStream(OutputStream.nullOutputStream()), depth + 1); changes++; continue;
                    }
                    output.writeByte(childType); output.writeUTF(name);
                    if (isEmpty(child)) { empty(childType, child, output, false, depth + 1); }
                    else copy(childType, child, output, depth + 1);
                }
                output.writeByte(0);
            }
            default -> throw new IOException("Unsupported NBT tag " + type);
        }
    }

    private static boolean isEmpty(Node node) { return node != null && node.edit != null && node.edit.operation().equals("empty"); }

    private void copyList(Node node, DataOutputStream output, int depth) throws IOException {
        int type = input.readUnsignedByte(), count = input.readInt();
        if (count < 0 || (type == 0 && count != 0)) throw new IOException("Invalid NBT list");
        // Only inventory-sized lists with direct empty-stack edits need buffering.
        boolean inventory = node != null && node.children.values().stream().anyMatch(TemplateNbtPatcher::isEmpty);
        var pending = inventory ? new ByteArrayOutputStream() : null;
        var body = inventory ? new DataOutputStream(pending) : output;
        int kept = count;
        if (!inventory) {
            if (node != null) for (int i = 0; i < count; i++) {
                var child = node.children.get(Integer.toString(i));
                if (child != null && child.edit != null && child.edit.operation().equals("remove")) kept--;
            }
            output.writeByte(type); output.writeInt(kept);
        }
        for (int i = 0; i < count; i++) {
            var child = node == null ? null : node.children.get(Integer.toString(i));
            if (child != null && child.edit != null && child.edit.operation().equals("remove")) {
                copy(type, null, new DataOutputStream(OutputStream.nullOutputStream()), depth); changes++;
                if (inventory) kept--;
            } else if (isEmpty(child)) {
                if (!empty(type, child, body, true, depth)) kept--;
            } else copy(type, child, body, depth);
        }
        if (inventory) { output.writeByte(type); output.writeInt(kept); pending.writeTo(output); }
    }

    private boolean empty(int type, Node node, DataOutputStream output, boolean listEntry, int depth) throws IOException {
        if (type != 10) throw new IOException("Expected item compound");
        var payload = new ByteArrayOutputStream();
        copy(type, null, new DataOutputStream(payload), depth);
        var item = new DataInputStream(new ByteArrayInputStream(payload.toByteArray()));
        boolean slot = false; String id = null;
        for (int childType; (childType = item.readUnsignedByte()) != 0;) {
            String name = item.readUTF();
            if (!Set.of("id", "Count", "count", "Slot", "slot", "tag", "components", "ForgeCaps").contains(name))
                throw new IOException("Unexpected empty-item field " + name);
            if (name.equals("id") && childType == 8) id = item.readUTF();
            else {
                if (name.equals("Slot") || name.equals("slot")) slot = true;
                var discard = new TemplateNbtPatcher(item, buffer);
                discard.copy(childType, null, new DataOutputStream(OutputStream.nullOutputStream()), depth + 1);
            }
        }
        if (!Objects.equals(node.edit.expectedId(), id)) throw new IOException("Unexpected item ID at " + node.edit.path());
        changes++;
        if (listEntry && slot) return false; // Sparse inventories omit empty entries; indexed lists retain an empty position.
        output.writeByte(0);
        return true;
    }

    private void transfer(long bytes, DataOutputStream output) throws IOException {
        while (bytes > 0) {
            int count = (int)Math.min(bytes, buffer.length); input.readFully(buffer, 0, count); output.write(buffer, 0, count); bytes -= count;
        }
    }
}
