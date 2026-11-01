package com.ajinkyadube.voicedeck.speech;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class SpeechCommandFilterTest {

    @Test
    public void removesSingleWordCommands() {
        SpeechCommandFilter filter = new SpeechCommandFilter(Arrays.asList("call", "please"));
        assertEquals(Collections.singletonList("john"), filter.filterTokens("Please call John"));
    }

    @Test
    public void removesMultiWordPhrasesBeforeShorterTerms() {
        SpeechCommandFilter filter = new SpeechCommandFilter(
                Arrays.asList("good", "good morning", "please"));
        assertEquals(Collections.singletonList("john"),
                filter.filterTokens("Good morning, please John"));
    }

    @Test
    public void doesNotRemoveTermsInsideOtherWords() {
        SpeechCommandFilter filter = new SpeechCommandFilter(Collections.singletonList("call"));
        assertEquals(Arrays.asList("callum", "john"), filter.filterTokens("Callum call John"));
    }

    @Test
    public void normalizesAccentAndPunctuationConsistently() {
        SpeechCommandFilter filter = new SpeechCommandFilter(Collections.singletonList("café"));
        assertEquals(Collections.singletonList("john"), filter.filterTokens("Cafe! John"));
    }

    @Test
    public void supportsDevanagariDictionaryTerms() {
        SpeechCommandFilter filter = new SpeechCommandFilter(Arrays.asList("कॉल", "कृपया"));
        assertEquals(Collections.singletonList("राहुल"), filter.filterTokens("कृपया कॉल राहुल"));
    }

    @Test
    public void preservesDevanagariVowelMarks() {
        SpeechCommandFilter filter = new SpeechCommandFilter(Arrays.asList("कॉल", "कृपया"));
        assertEquals(Collections.singletonList("राहुल"),
                filter.filterTokens("कृपया कॉल राहुल"));
    }

    @Test
    public void emptyDictionaryPreservesInputTokens() {
        SpeechCommandFilter filter = SpeechCommandFilter.empty();
        assertEquals(Arrays.asList("please", "call", "john"),
                filter.filterTokens("Please call John"));
    }
}
