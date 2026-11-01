package com.voicedeck.app;

import com.voicedeck.app.data.local.entity.ContactAlias;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContactAliasMatcherMapTest {

    @Test
    public void mapsMultipleAliasesForOneCurrentContact() {
        List<ContactAlias> rows = Arrays.asList(
                alias(10, "Aai"), alias(10, "Mummy"), alias(10, "Mom"));

        Map<Long, List<String>> result = ContactAliasMatcherMap.fromAliases(
                rows, Collections.singleton(10L));

        assertEquals(Arrays.asList("Aai", "Mummy", "Mom"), result.get(10L));
    }

    @Test
    public void ignoresAliasesForContactsNotPresentOnDevice() {
        List<ContactAlias> rows = Arrays.asList(
                alias(10, "Aai"), alias(20, "Rahul"));
        Set<Long> currentIds = Collections.singleton(10L);

        Map<Long, List<String>> result = ContactAliasMatcherMap.fromAliases(rows, currentIds);

        assertTrue(result.containsKey(10L));
        assertFalse(result.containsKey(20L));
    }

    @Test
    public void ignoresNullAndBlankAliasRows() {
        List<ContactAlias> rows = Arrays.asList(
                alias(10, "  "), alias(10, "Mummy"));

        Map<Long, List<String>> result = ContactAliasMatcherMap.fromAliases(
                rows, Collections.singleton(10L));

        assertEquals(Collections.singletonList("Mummy"), result.get(10L));
    }

    private static ContactAlias alias(long contactId, String value) {
        return new ContactAlias(contactId, value,
                value == null ? "" : value.trim().toLowerCase(), "en-IN", 1L);
    }
}
