package com.ajinkyadube.voicedeck;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Literal type-as-you-go search. The supplied aliases are scoped to the selected language. */
public final class ManualContactSearch {
    private static final class Entry {
        final ContactItem contact;
        final List<String> names = new ArrayList<>();
        final String phone;

        Entry(ContactItem contact, List<String> aliases) {
            this.contact = contact;
            names.add(normalize(contact.name));
            if (aliases != null) for (String alias : aliases) names.add(normalize(alias));
            phone = digits(contact.phone);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public ManualContactSearch(List<ContactItem> contacts, Map<Long, List<String>> aliases) {
        if (contacts == null) return;
        Map<Long, List<String>> safeAliases = aliases == null ? Collections.emptyMap() : aliases;
        for (ContactItem contact : contacts) {
            if (contact != null) entries.add(new Entry(contact, safeAliases.get(contact.id)));
        }
    }

    public List<ContactItem> search(String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) return Collections.emptyList();
        // Only interpret numeric queries as numbers, so "Mom 2" cannot match every number with 2.
        String number = query != null && query.matches("[\\p{N}\\s()+.\\-]+") ? digits(query) : "";
        List<ContactItem> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Entry entry : entries) {
            boolean matches = !number.isEmpty() && entry.phone.contains(number);
            for (String name : entry.names) {
                if (name.contains(normalized)) { matches = true; break; }
            }
            if (matches && seen.add(entry.contact.id)) result.add(entry.contact);
        }
        return result;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("(?<=\\p{IsLatin})\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ").trim();
    }

    private static String digits(String value) {
        StringBuilder result = new StringBuilder();
        if (value != null) for (int i = 0; i < value.length(); i++) {
            int digit = Character.digit(value.charAt(i), 10);
            if (digit >= 0) result.append(digit);
        }
        return result.toString();
    }
}
