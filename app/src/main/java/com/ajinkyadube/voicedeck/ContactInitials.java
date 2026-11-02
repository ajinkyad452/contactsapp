package com.ajinkyadube.voicedeck;

import java.text.BreakIterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Letter/number initials without emoji, broken surrogate pairs, or detached vowel marks. */
public final class ContactInitials {
    private static final Pattern NAME_WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*");

    private ContactInitials() { }

    public static String fromName(String name) {
        Matcher words = NAME_WORD.matcher(name == null ? "" : name);
        String first = null;
        String last = null;
        int count = 0;
        while (words.find()) {
            count++;
            if (first == null) first = words.group();
            last = words.group();
        }
        if (first == null) return "•";
        String result = firstCharacter(first);
        if (count > 1) result += firstCharacter(last);
        return result.toUpperCase(Locale.ROOT);
    }

    private static String firstCharacter(String word) {
        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(word);
        return word.substring(0, characters.next());
    }
}
