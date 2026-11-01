package com.ajinkyadube.voicedeck.speech;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure-Java filter for removing locally configured command, greeting, and filler
 * terms from recognized speech before partial contact-token matching.
 * This class has no Android or Room dependency.
 */
public final class SpeechCommandFilter {

    private final List<List<String>> filterPhrases;

    public SpeechCommandFilter(List<String> terms) {
        List<List<String>> phrases = new ArrayList<>();
        if (terms != null) {
            for (String term : terms) {
                List<String> tokens = tokenize(term);
                if (!tokens.isEmpty() && !phrases.contains(tokens)) {
                    phrases.add(tokens);
                }
            }
        }
        phrases.sort(Comparator.comparingInt((List<String> value) -> value.size()).reversed());
        this.filterPhrases = Collections.unmodifiableList(phrases);
    }

    public static SpeechCommandFilter empty() {
        return new SpeechCommandFilter(Collections.emptyList());
    }

    /** Removes exact, token-boundary dictionary terms and phrases. */
    public List<String> filterTokens(String recognizedText) {
        List<String> tokens = new ArrayList<>(tokenize(recognizedText));
        for (List<String> phrase : filterPhrases) {
            removePhrase(tokens, phrase);
        }
        return Collections.unmodifiableList(tokens);
    }

    public static String normalizeTerm(String value) {
        return join(tokenize(value));
    }

    private static void removePhrase(List<String> tokens, List<String> phrase) {
        if (phrase.isEmpty() || phrase.size() > tokens.size()) return;
        int index = 0;
        while (index <= tokens.size() - phrase.size()) {
            boolean matches = true;
            for (int offset = 0; offset < phrase.size(); offset++) {
                if (!tokens.get(index + offset).equals(phrase.get(offset))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                for (int offset = 0; offset < phrase.size(); offset++) tokens.remove(index);
            } else {
                index++;
            }
        }
    }

    private static List<String> tokenize(String value) {
        String normalized = Normalizer.normalize(
                        value == null ? "" : value,
                        Normalizer.Form.NFD)
                .replaceAll("(?<=\\p{IsLatin})\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ")
                .trim();
        if (normalized.isEmpty()) return Collections.emptyList();
        String[] parts = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>(parts.length);
        Collections.addAll(tokens, parts);
        return tokens;
    }

    private static String join(List<String> tokens) {
        StringBuilder result = new StringBuilder();
        for (String token : tokens) {
            if (result.length() > 0) result.append(' ');
            result.append(token);
        }
        return result.toString();
    }
}
