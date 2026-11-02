package com.ajinkyadube.voicedeck;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContactInitialsTest {
    @Test public void skipsTrailingEmoji() {
        assertEquals("S", ContactInitials.fromName("Sample 🥞"));
        assertEquals("S", ContactInitials.fromName("Sample 👩‍💻"));
    }

    @Test public void handlesEmojiBeforeAndBetweenNameWords() {
        assertEquals("SA", ContactInitials.fromName("🌼 Sample 🤖 Alpha 💛"));
    }

    @Test public void handlesSingleAndMultipleNames() {
        assertEquals("S", ContactInitials.fromName("sample"));
        assertEquals("SA", ContactInitials.fromName("  sample   middle alpha  "));
    }

    @Test public void ignoresPunctuation() {
        assertEquals("SA", ContactInitials.fromName("★ (sample) / alpha!"));
    }

    @Test public void preservesCombiningMarks() {
        assertEquals("A\u0301", ContactInitials.fromName("a\u0301lpha"));
        assertEquals("सी", ContactInitials.fromName("सीमा"));
    }

    @Test public void handlesSupplementaryLettersWithoutSplittingSurrogates() {
        String result = ContactInitials.fromName("\uD801\uDC00name");
        assertEquals(1, result.codePointCount(0, result.length()));
        assertTrue(Character.isSupplementaryCodePoint(result.codePointAt(0)));
    }

    @Test public void givesNeutralFallbackForNamesWithoutLettersOrNumbers() {
        assertEquals("•", ContactInitials.fromName(null));
        assertEquals("•", ContactInitials.fromName("  "));
        assertEquals("•", ContactInitials.fromName("💛 🥞"));
    }

    @Test public void uppercaseDoesNotDependOnDeviceLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("I", ContactInitials.fromName("item"));
        } finally { Locale.setDefault(previous); }
    }
}
