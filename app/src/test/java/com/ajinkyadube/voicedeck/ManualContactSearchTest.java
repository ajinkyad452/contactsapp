package com.ajinkyadube.voicedeck;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ManualContactSearchTest {
    private final ContactItem first = contact(1, "Sample Alpha", "SAMPLE-NUMBER-000-001");
    private final ContactItem second = contact(2, "Sample Beta", "SAMPLE-NUMBER-000-002");
    private final List<ContactItem> contacts = Arrays.asList(first, second);

    @Test public void findsFullAndPartialAliasesIgnoringCaseAndWhitespace() {
        ManualContactSearch search = search(Map.of(1L, Arrays.asList("Home Friend", "Buddy")));
        assertEquals(Collections.singletonList(first), search.search("  HOME fr "));
        assertEquals(Collections.singletonList(first), search.search("bud"));
    }

    @Test public void returnsBothContactsForSharedAlias() {
        ManualContactSearch search = search(Map.of(1L, List.of("Friend"), 2L, List.of("Friend")));
        assertEquals(contacts, search.search("friend"));
    }

    @Test public void doesNotOverrideRealNamesWithAliases() {
        ManualContactSearch search = search(Map.of(1L, List.of("Sample Beta")));
        assertEquals(contacts, search.search("Sample Beta"));
    }

    @Test public void returnsContactOnceEvenWhenSeveralNamesMatch() {
        ManualContactSearch search = new ManualContactSearch(Arrays.asList(first, first),
                Map.of(1L, Arrays.asList("Sample", "Sample Alpha", "Sample A")));
        assertEquals(Collections.singletonList(first), search.search("sample"));
    }

    @Test public void preservesHindiAndMarathiVowelMarks() {
        ManualContactSearch search = search(Map.of(1L, List.of("आई"), 2L, List.of("माँ")));
        assertEquals(Collections.singletonList(first), search.search("आई"));
        assertEquals(Collections.singletonList(second), search.search("माँ"));
        assertTrue(search.search("मौ").isEmpty());
    }

    @Test public void normalizesLatinAccentsAndPunctuation() {
        ManualContactSearch search = search(Map.of(1L, List.of("Amígo-Home")));
        assertEquals(Collections.singletonList(first), search.search("amigo home"));
    }

    @Test public void doesNotFilterTypedCommandOrGreetingWords() {
        ManualContactSearch search = search(Map.of(1L, List.of("Hello")));
        assertEquals(Collections.singletonList(first), search.search("hello"));
    }

    @Test public void keepsNameAndFormattedPhoneSearchWorking() {
        ManualContactSearch search = search(Collections.emptyMap());
        assertEquals(Collections.singletonList(second), search.search("Beta"));
        assertEquals(Collections.singletonList(first), search.search("000 001"));
        assertEquals(Collections.singletonList(first), search.search("००० ००१"));
    }

    @Test public void doesNotTreatMixedTextAsPhoneQuery() {
        assertTrue(search(Collections.emptyMap()).search("unknown 000001").isEmpty());
    }

    @Test public void emptyOrPunctuationQueriesHaveNoResults() {
        ManualContactSearch search = search(Collections.emptyMap());
        assertTrue(search.search(null).isEmpty());
        assertTrue(search.search("   ").isEmpty());
        assertTrue(search.search("---").isEmpty());
    }

    @Test public void ignoresAliasesForMissingContactsAndOtherLanguageIndexes() {
        ManualContactSearch english = search(Map.of(1L, List.of("Mom"), 99L, List.of("Missing")));
        ManualContactSearch marathi = search(Map.of(1L, List.of("आई")));
        assertTrue(english.search("Missing").isEmpty());
        assertTrue(english.search("आई").isEmpty());
        assertTrue(marathi.search("Mom").isEmpty());
        assertEquals(Collections.singletonList(first), marathi.search("आई"));
    }

    @Test public void reloadedIndexReflectsAliasEditsAndDeletion() {
        assertEquals(Collections.singletonList(first), search(Map.of(1L, List.of("Buddy"))).search("buddy"));
        assertTrue(search(Map.of(1L, List.of("Friend"))).search("buddy").isEmpty());
        assertTrue(search(Collections.emptyMap()).search("friend").isEmpty());
    }

    private ManualContactSearch search(Map<Long, List<String>> aliases) {
        return new ManualContactSearch(contacts, aliases);
    }

    private static ContactItem contact(long id, String name, String phone) {
        ContactItem item = new ContactItem();
        item.id = id; item.name = name; item.phone = phone;
        return item;
    }
}
