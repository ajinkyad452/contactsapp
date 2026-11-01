package com.voicedeck.app.speech;

import com.voicedeck.app.data.local.entity.SpeechDictionaryEntry;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class SpeechDictionaryDefaultsTest {

    @Test
    public void providesStarterTermsForAllSupportedLanguages() {
        List<SpeechDictionaryEntry> entries = SpeechDictionaryDefaults.createEntries();
        assertTrue(!SpeechDictionaryDefaults.termsForLanguage(entries, "en-IN").isEmpty());
        assertTrue(!SpeechDictionaryDefaults.termsForLanguage(entries, "hi-IN").isEmpty());
        assertTrue(!SpeechDictionaryDefaults.termsForLanguage(entries, "mr-IN").isEmpty());
    }

    @Test
    public void marksBundledTermsAsDefaultsAndProtectsEssentialCommands() {
        List<SpeechDictionaryEntry> entries = SpeechDictionaryDefaults.createEntries();
        for (SpeechDictionaryEntry entry : entries) {
            assertTrue(entry.isDefault);
            if (("en-IN".equals(entry.languageTag) && "call".equals(entry.normalizedTerm))
                    || (("hi-IN".equals(entry.languageTag) || "mr-IN".equals(entry.languageTag))
                    && "कॉल".equals(entry.normalizedTerm))) {
                assertTrue(entry.isProtected);
            }
        }
    }

    @Test
    public void customEntriesAreNotMarkedAsDefaults() {
        SpeechDictionaryEntry custom = new SpeechDictionaryEntry(
                "ring my friend", "ring my friend", "en-IN",
                SpeechDictionaryEntry.CATEGORY_COMMAND, true
        );
        assertTrue(!custom.isDefault);
        assertTrue(!custom.isProtected);
    }

    @Test
    public void disabledEntriesAreNotIncludedInTheFilter() {
        List<SpeechDictionaryEntry> entries = SpeechDictionaryDefaults.createEntries();
        for (SpeechDictionaryEntry entry : entries) {
            if ("en-IN".equals(entry.languageTag)) entry.enabled = false;
        }
        assertTrue(SpeechDictionaryDefaults.termsForLanguage(entries, "en-IN").isEmpty());
    }
}
