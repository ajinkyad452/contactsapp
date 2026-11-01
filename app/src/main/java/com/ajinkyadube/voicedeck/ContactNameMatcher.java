package com.ajinkyadube.voicedeck;

import com.ajinkyadube.voicedeck.speech.SpeechCommandFilter;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
     * @param aliases locally configured aliases keyed by ContactItem.id; may be empty
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
        return resolve(spokenText, SpeechCommandFilter.empty());
    }

    public Result resolve(String spokenText, SpeechCommandFilter commandFilter) {
        // Preserve the original words for complete-name checks. A legitimate contact
        // may contain a word that is also a command or greeting (for example, "Hello Tunes").
        List<String> originalTokens = tokenize(spokenText);
        if (originalTokens.isEmpty() || index.isEmpty()) {
            return new Result(Status.NO_MATCH, Collections.emptyList());
        }

        // Only remove command/greeting words for the weaker partial-token fallback.
        SpeechCommandFilter safeFilter = commandFilter == null
                ? SpeechCommandFilter.empty() : commandFilter;
        Set<String> candidateTokenSet = new HashSet<>(safeFilter.filterTokens(spokenText));
        Map<ContactItem, Integer> scores = new HashMap<>();
        int highestScore = 0;

        for (IndexedContact entry : index) {
            int score = score(entry, originalTokens, candidateTokenSet);
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
                             List<String> originalTokens,
                             Set<String> candidateTokenSet) {
        int bestScore = 0;
        int matchedNameTokens = 0;

        // Multi-word full names/aliases are stronger than a single token. A
        // one-word alias has the same strength as a first-name match so it cannot
        // silently override another contact whose real name contains that word.
        for (List<String> phrase : entry.namePhrases) {
            if (containsPhrase(originalTokens, phrase)) {
                int phraseScore = phrase.size() == 1
                        ? 110
                        : 1000 + (phrase.size() * 20);
                bestScore = Math.max(bestScore, phraseScore);
            }
        }

        // First names and surnames can independently identify a contact, but only
        // after command/greeting words have been removed. An empty candidate list
        // therefore cannot accidentally match a greeting token such as "hello".
        if (candidateTokenSet.isEmpty()) return bestScore;

        for (String nameToken : entry.nameTokens) {
            if (candidateTokenSet.contains(nameToken)) matchedNameTokens++;
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
}
