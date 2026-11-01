package com.voicedeck.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

/**
 * Local-only contact name detection for VoiceDeck.
 *
 * The matcher indexes the names already loaded from Android Contacts.
 * It does not access the network and does not call an AI service.
 */
public final class ContactNameMatcher {

    public enum Status {
        MATCH_FOUND,
        MULTIPLE_MATCHES,
        NO_MATCH
    }

    public static final class Result {
        private final Status status;
        private final List<ContactItem> matches;

        private Result(Status status, List<ContactItem> matches) {
            this.status = status;
            this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
        }

        public Status getStatus() {
            return status;
        }

        public List<ContactItem> getMatches() {
            return matches;
        }
    }

    private static final Set<String> COMMAND_WORDS =
        new HashSet<>(Arrays.asList(
                "call",
                "contact",
                "phone",
                "dial",
                "number",
                "mobile",
                "please",
                "hey",
                "hi",
                "find",
                "search",
                "show",
                "open",
                "get",
                "the",
                "a",
                "an",
                "my",
                "me",
                "to",
                "for"
        ));

    private static final class IndexedContact {
        final ContactItem contact;
        final List<List<String>> namePhrases = new ArrayList<>();
        final Set<String> nameTokens = new HashSet<>();

        IndexedContact(ContactItem contact, List<String> aliases) {
            this.contact = contact;
            addName(contact.name);
            if (aliases != null) {
                for (String alias : aliases) addName(alias);
            }
        }

        private void addName(String value) {
            List<String> tokens = tokenize(value);
            if (tokens.isEmpty()) return;
            namePhrases.add(tokens);
            nameTokens.addAll(tokens);
        }
    }

    private final List<IndexedContact> index = new ArrayList<>();

    /**
     * @param contacts contacts already read from the device's Contacts Provider
     * @param aliases optional locally configured aliases keyed by ContactItem.id.
     *                Pass an empty map until alias management is introduced.
     */
    public ContactNameMatcher(List<ContactItem> contacts,
                              Map<Long, List<String>> aliases) {
        if (contacts == null) return;

        Map<Long, List<String>> safeAliases =
                aliases == null ? Collections.emptyMap() : aliases;

        for (ContactItem contact : contacts) {
            if (contact == null || contact.name == null || contact.name.trim().isEmpty()) {
                continue;
            }
            index.add(new IndexedContact(contact, safeAliases.get(contact.id)));
        }
    }

    public Result resolve(String spokenText) {
        List<String> spokenTokens = removeCommandWords(tokenize(spokenText));
        if (spokenTokens.isEmpty() || index.isEmpty()) {
            return new Result(Status.NO_MATCH, Collections.emptyList());
        }

        Set<String> spokenTokenSet = new HashSet<>(spokenTokens);
        Map<ContactItem, Integer> scores = new HashMap<>();
        int highestScore = 0;

        for (IndexedContact entry : index) {
            int score = score(entry, spokenTokens, spokenTokenSet);
            if (score <= 0) continue;

            scores.put(entry.contact, score);
            if (score > highestScore) highestScore = score;
        }

        if (highestScore == 0) {
            return new Result(Status.NO_MATCH, Collections.emptyList());
        }

        List<ContactItem> bestMatches = new ArrayList<>();
        for (IndexedContact entry : index) {
            Integer score = scores.get(entry.contact);
            if (score != null && score == highestScore) {
                bestMatches.add(entry.contact);
            }
        }

        Status status = bestMatches.size() == 1
                ? Status.MATCH_FOUND
                : Status.MULTIPLE_MATCHES;

        return new Result(status, bestMatches);
    }

    private static int score(IndexedContact entry,
                             List<String> spokenTokens,
                             Set<String> spokenTokenSet) {
        int bestScore = 0;
        int matchedNameTokens = 0;

        // A complete first+last name or alias is stronger than a single token.
        for (List<String> phrase : entry.namePhrases) {
            if (containsPhrase(spokenTokens, phrase)) {
                int phraseScore = 1000 + (phrase.size() * 20);
                bestScore = Math.max(bestScore, phraseScore);
            }
        }

        // First names and surnames can independently identify a contact.
        for (String nameToken : entry.nameTokens) {
            if (spokenTokenSet.contains(nameToken)) matchedNameTokens++;
        }

        if (matchedNameTokens > 0) {
            int tokenScore = 100 + (matchedNameTokens * 10);
            bestScore = Math.max(bestScore, tokenScore);
        }

        return bestScore;
    }

    private static boolean containsPhrase(List<String> sentence, List<String> phrase) {
        if (phrase.isEmpty() || phrase.size() > sentence.size()) return false;

        for (int start = 0; start <= sentence.size() - phrase.size(); start++) {
            boolean matches = true;
            for (int offset = 0; offset < phrase.size(); offset++) {
                if (!sentence.get(start + offset).equals(phrase.get(offset))) {
                    matches = false;
                    break;
                }
            }
            if (matches) return true;
        }
        return false;
    }

    private static List<String> removeCommandWords(List<String> tokens) {
        List<String> filteredTokens = new ArrayList<>();

        for (String token : tokens) {
            if (!COMMAND_WORDS.contains(token)) {
                filteredTokens.add(token);
            }
        }

        return filteredTokens;
    }

    private static List<String> tokenize(String value) {
        String normalized = Normalizer.normalize(
                        value == null ? "" : value,
                        Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();

        if (normalized.isEmpty()) return Collections.emptyList();

        String[] parts = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>(parts.length);
        Collections.addAll(tokens, parts);
        return tokens;
    }
}
