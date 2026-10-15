package com.sanhiruzu.packrepairs;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuddycardsRulebookRepairTest {
    @Test void leavesCustomAndUpstreamFixedDeclarationsAlone() {
        byte[] custom = "{\"name\":\"Custom book\"}".getBytes(StandardCharsets.UTF_8);
        byte[] fixed = "{\"use_resource_pack\":true}".getBytes(StandardCharsets.UTF_8);
        assertSame(custom, BuddycardsRulebookRepair.migrateDeclaration(BuddycardsRulebookRepair.BOOK, false, custom));
        assertSame(fixed, BuddycardsRulebookRepair.migrateDeclaration(BuddycardsRulebookRepair.BOOK, false, fixed));
        assertFalse(BuddycardsRulebookRepair.matches("patchouli_books/buddycards_rules/en_us/entries/unknown.json", custom));
    }

    @Test void migratesOnlyTheOriginalInternalRulebook() throws Exception {
        Path fixture = Path.of("../private/buddycards-rulebook-20261008/book.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(fixture), "Local upstream fixture stays outside source control");
        byte[] original = Files.readAllBytes(fixture);
        assertTrue(BuddycardsRulebookRepair.matches(BuddycardsRulebookRepair.PREFIX + "book.json", original));
        assertSame(original, BuddycardsRulebookRepair.migrateDeclaration("another:book", false, original));
        assertSame(original, BuddycardsRulebookRepair.migrateDeclaration(BuddycardsRulebookRepair.BOOK, true, original));
        var before = JsonParser.parseString(new String(original, StandardCharsets.UTF_8)).getAsJsonObject();
        var after = JsonParser.parseString(new String(BuddycardsRulebookRepair.migrateDeclaration(
                BuddycardsRulebookRepair.BOOK, false, original), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(after.remove("use_resource_pack").getAsBoolean());
        assertEquals(before, after, "Preserve every original declaration field");
    }
}
