package com.voicedeck.app.speech;

import com.voicedeck.app.data.local.entity.SpeechDictionaryEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bundled starter vocabulary inserted into the local Room database on first use. */
public final class SpeechDictionaryDefaults {

    private SpeechDictionaryDefaults() {}

    public static List<SpeechDictionaryEntry> createEntries() {
        List<SpeechDictionaryEntry> entries = new ArrayList<>();

        add(entries, "en-IN", SpeechDictionaryEntry.CATEGORY_COMMAND,
                "call", "dial", "phone", "contact", "number", "mobile",
                "find", "search", "show", "open", "get");
        add(entries, "en-IN", SpeechDictionaryEntry.CATEGORY_GREETING,
                "hey", "hi", "hello", "good morning", "good afternoon", "good evening");
        add(entries, "en-IN", SpeechDictionaryEntry.CATEGORY_FILLER,
                "please", "the", "a", "an", "my", "me", "to", "for");

        add(entries, "hi-IN", SpeechDictionaryEntry.CATEGORY_COMMAND,
                "कॉल", "फोन", "संपर्क", "ढूंढो", "खोजो", "दिखाओ", "खोलो");
        add(entries, "hi-IN", SpeechDictionaryEntry.CATEGORY_GREETING,
                "नमस्ते", "नमस्कार", "हैलो", "हेलो", "सुप्रभात");
        add(entries, "hi-IN", SpeechDictionaryEntry.CATEGORY_FILLER,
                "कृपया", "मेरा", "मेरी", "मुझे", "के लिए");

        add(entries, "mr-IN", SpeechDictionaryEntry.CATEGORY_COMMAND,
                "कॉल", "फोन", "संपर्क", "शोधा", "दाखवा", "उघडा");
        add(entries, "mr-IN", SpeechDictionaryEntry.CATEGORY_GREETING,
                "नमस्कार", "हॅलो", "सुप्रभात");
        add(entries, "mr-IN", SpeechDictionaryEntry.CATEGORY_FILLER,
                "कृपया", "माझा", "माझी", "मला", "साठी");

        return entries;
    }

    public static List<String> termsForLanguage(List<SpeechDictionaryEntry> entries,
                                                String languageTag) {
        List<String> terms = new ArrayList<>();
        if (entries == null || languageTag == null) return terms;
        for (SpeechDictionaryEntry entry : entries) {
            if (entry != null && entry.enabled && languageTag.equals(entry.languageTag)) {
                terms.add(entry.normalizedTerm);
            }
        }
        return terms;
    }

    public static boolean isProtectedDefault(String languageTag, String category,
                                             String normalizedTerm) {
        if (!SpeechDictionaryEntry.CATEGORY_COMMAND.equals(category)) return false;
        return ("en-IN".equals(languageTag) && "call".equals(normalizedTerm))
                || (("hi-IN".equals(languageTag) || "mr-IN".equals(languageTag))
                && "कॉल".equals(normalizedTerm));
    }

    private static void add(List<SpeechDictionaryEntry> target,
                            String languageTag,
                            String category,
                            String... terms) {
        for (String term : Arrays.asList(terms)) {
            target.add(new SpeechDictionaryEntry(
                    term,
                    SpeechCommandFilter.normalizeTerm(term),
                    languageTag,
                    category,
                    true,
                    true,
                    isProtectedDefault(languageTag, category,
                            SpeechCommandFilter.normalizeTerm(term))
            ));
        }
    }
}
