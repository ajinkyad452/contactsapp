package com.voicedeck.app;

import org.junit.Test;

import com.voicedeck.app.speech.SpeechCommandFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class ContactNameMatcherTest {

    @Test
    public void matchesCompleteNameInsideCommandSentence() {
        ContactItem john = contact(1, "John Smith");

        ContactNameMatcher.Result result = matcher(john).resolve("Please call John Smith", englishFilter());

        assertMatch(result, john);
    }

    @Test
    public void preservesGreetingWordWhenItIsPartOfCompleteContactName() {
        ContactItem helloTunes = contact(1, "Hello Tunes");

        ContactNameMatcher.Result result = matcher(helloTunes).resolve("Please call Hello Tunes", englishFilter());

        assertMatch(result, helloTunes);
    }

    @Test
    public void greetingAloneDoesNotPartiallyMatchMultiWordContact() {
        ContactItem helloTunes = contact(1, "Hello Tunes");

        ContactNameMatcher.Result result = matcher(helloTunes).resolve("hello", englishFilter());

        assertNoMatch(result);
    }

    @Test
    public void recognizedGreetingDoesNotPartiallyMatchMultiWordContact() {
        ContactItem helloTunes = contact(1, "Hello Tunes");

        ContactNameMatcher.Result result = matcher(helloTunes).resolve("hellooo", englishFilter());

        assertNoMatch(result);
    }

    @Test
    public void exactSingleWordContactCanStillBeMatchedWhenWordIsGreeting() {
        ContactItem hello = contact(1, "Hello");

        ContactNameMatcher.Result result = matcher(hello).resolve("hello", englishFilter());

        assertMatch(result, hello);
    }

    @Test
    public void fullNameMatchTakesPriorityOverAnotherContactsPartialToken() {
        ContactItem johnSmith = contact(1, "John Smith");
        ContactItem smith = contact(2, "Smith");

        ContactNameMatcher.Result result = matcher(johnSmith, smith).resolve("Call John Smith", englishFilter());

        assertMatch(result, johnSmith);
    }

    @Test
    public void supportsFirstNamePartialMatch() {
        ContactItem johnSmith = contact(1, "John Smith");

        ContactNameMatcher.Result result = matcher(johnSmith).resolve("Call John", englishFilter());

        assertMatch(result, johnSmith);
    }

    @Test
    public void supportsSurnamePartialMatch() {
        ContactItem johnSmith = contact(1, "John Smith");

        ContactNameMatcher.Result result = matcher(johnSmith).resolve("Call Smith", englishFilter());

        assertMatch(result, johnSmith);
    }

    @Test
    public void reportsAmbiguousFirstNameInsteadOfChoosing() {
        ContactItem johnSmith = contact(1, "John Smith");
        ContactItem johnDoe = contact(2, "John Doe");

        ContactNameMatcher.Result result = matcher(johnSmith, johnDoe).resolve("Call John", englishFilter());

        assertEquals(ContactNameMatcher.Status.MULTIPLE_MATCHES, result.getStatus());
        assertEquals(2, result.getMatches().size());
    }

    @Test
    public void reportsAmbiguousSurnameInsteadOfChoosing() {
        ContactItem johnSmith = contact(1, "John Smith");
        ContactItem janeSmith = contact(2, "Jane Smith");

        ContactNameMatcher.Result result = matcher(johnSmith, janeSmith).resolve("Call Smith", englishFilter());

        assertEquals(ContactNameMatcher.Status.MULTIPLE_MATCHES, result.getStatus());
        assertEquals(2, result.getMatches().size());
    }

    @Test
    public void commandOnlyInputReturnsNoMatch() {
        ContactItem john = contact(1, "John Smith");

        ContactNameMatcher.Result result = matcher(john).resolve("Please call my contact", englishFilter());

        assertNoMatch(result);
    }

    @Test
    public void emptyAndNullInputReturnNoMatch() {
        ContactItem john = contact(1, "John Smith");
        ContactNameMatcher matcher = matcher(john);

        assertNoMatch(matcher.resolve("  !!!  ", englishFilter()));
        assertNoMatch(matcher.resolve(null, englishFilter()));
    }

    @Test
    public void matchesConfiguredAliasAsCompletePhrase() {
        ContactItem john = contact(1, "John Smith");
        Map<Long, List<String>> aliases = new HashMap<>();
        aliases.put(john.id, Collections.singletonList("Johnny Boy"));
        ContactNameMatcher matcher = new ContactNameMatcher(
                Collections.singletonList(john), aliases);

        ContactNameMatcher.Result result = matcher.resolve("Please call Johnny Boy", englishFilter());

        assertMatch(result, john);
    }

    @Test
    public void oneContactCanBeMatchedByAnyOfMultipleAliases() {
        ContactItem sunita = contact(1, "Sunita Sharma");
        Map<Long, List<String>> aliases = new HashMap<>();
        aliases.put(sunita.id, Arrays.asList("Aai", "Mummy", "Mom"));
        ContactNameMatcher matcher = new ContactNameMatcher(
                Collections.singletonList(sunita), aliases);

        assertMatch(matcher.resolve("Please call Aai", englishFilter()), sunita);
        assertMatch(matcher.resolve("Call Mummy", englishFilter()), sunita);
        assertMatch(matcher.resolve("I want to speak with Mom", englishFilter()), sunita);
    }

    @Test
    public void duplicateAliasAcrossContactsReturnsMultipleMatches() {
        ContactItem rahulSharma = contact(1, "Rahul Sharma");
        ContactItem rahulPatil = contact(2, "Rahul Patil");
        Map<Long, List<String>> aliases = new HashMap<>();
        aliases.put(rahulSharma.id, Collections.singletonList("Rahul"));
        aliases.put(rahulPatil.id, Collections.singletonList("Rahul"));
        ContactNameMatcher matcher = new ContactNameMatcher(
                Arrays.asList(rahulSharma, rahulPatil), aliases);

        ContactNameMatcher.Result result = matcher.resolve("Call Rahul", englishFilter());

        assertEquals(ContactNameMatcher.Status.MULTIPLE_MATCHES, result.getStatus());
        assertEquals(2, result.getMatches().size());
        assertSame(rahulSharma, result.getMatches().get(0));
        assertSame(rahulPatil, result.getMatches().get(1));
    }

    @Test
    public void exactContactNameAndAnotherContactsAliasRemainAmbiguous() {
        ContactItem rahulSharma = contact(1, "Rahul Sharma");
        ContactItem amitDesai = contact(2, "Amit Desai");
        Map<Long, List<String>> aliases = new HashMap<>();
        aliases.put(amitDesai.id, Collections.singletonList("Rahul"));
        ContactNameMatcher matcher = new ContactNameMatcher(
                Arrays.asList(rahulSharma, amitDesai), aliases);

        ContactNameMatcher.Result result = matcher.resolve("Call Rahul", englishFilter());

        assertEquals(ContactNameMatcher.Status.MULTIPLE_MATCHES, result.getStatus());
        assertEquals(2, result.getMatches().size());
    }

    @Test
    public void normalizesAccentsAndPunctuation() {
        ContactItem jose = contact(1, "José O'Neil");

        ContactNameMatcher.Result result = matcher(jose).resolve("Call Jose O Neil!", englishFilter());

        assertMatch(result, jose);
    }

    @Test
    public void matchesHindiContactNameWithoutStrippingDevanagariVowelMarks() {
        ContactItem rahul = contact(1, "राहुल शर्मा");
        SpeechCommandFilter hindiFilter = new SpeechCommandFilter(
                Arrays.asList("कॉल", "फोन", "कृपया", "नमस्ते"));

        ContactNameMatcher.Result result = matcher(rahul).resolve("कृपया कॉल राहुल शर्मा", hindiFilter);

        assertMatch(result, rahul);
    }

    @Test
    public void exactContactNameMadeOfFilterWordsIsStillMatchable() {
        ContactItem callMe = contact(1, "Call Me");

        ContactNameMatcher.Result result = matcher(callMe).resolve("Call Me", englishFilter());

        assertMatch(result, callMe);
    }

    private static SpeechCommandFilter englishFilter() {
        return new SpeechCommandFilter(Arrays.asList(
                "call", "dial", "phone", "contact", "number", "mobile",
                "find", "search", "show", "open", "get", "hey", "hi", "hello",
                "good morning", "good afternoon", "good evening",
                "please", "the", "a", "an", "my", "me", "to", "for"
        ));
    }

    private static ContactNameMatcher matcher(ContactItem... contacts) {
        return new ContactNameMatcher(Arrays.asList(contacts), Collections.emptyMap());
    }

    private static ContactItem contact(long id, String name) {
        ContactItem contact = new ContactItem();
        contact.id = id;
        contact.name = name;
        return contact;
    }

    private static void assertMatch(ContactNameMatcher.Result result, ContactItem expected) {
        assertEquals(ContactNameMatcher.Status.MATCH_FOUND, result.getStatus());
        assertEquals(1, result.getMatches().size());
        assertSame(expected, result.getMatches().get(0));
    }

    private static void assertNoMatch(ContactNameMatcher.Result result) {
        assertEquals(ContactNameMatcher.Status.NO_MATCH, result.getStatus());
        assertEquals(0, result.getMatches().size());
    }
}
