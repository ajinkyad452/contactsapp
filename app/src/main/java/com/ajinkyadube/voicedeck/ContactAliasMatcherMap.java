package com.ajinkyadube.voicedeck;

import com.ajinkyadube.voicedeck.data.local.entity.ContactAlias;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the in-memory alias index without retaining aliases for missing contacts. */
public final class ContactAliasMatcherMap {

    private ContactAliasMatcherMap() { }

    /**
     * Converts Room alias rows into the matcher input shape. The DAO has already
     * filtered rows by language; this method filters stale Contact Provider IDs.
     */
    public static Map<Long, List<String>> fromAliases(
            List<ContactAlias> aliases,
            Set<Long> currentContactIds
    ) {
        if (aliases == null || aliases.isEmpty()
                || currentContactIds == null || currentContactIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> validIds = new HashSet<>(currentContactIds);
        Map<Long, List<String>> result = new HashMap<>();
        for (ContactAlias alias : aliases) {
            if (alias == null || !validIds.contains(alias.contactId)
                    || alias.alias == null || alias.alias.trim().isEmpty()) {
                continue;
            }
            List<String> contactAliases = result.get(alias.contactId);
            if (contactAliases == null) {
                contactAliases = new ArrayList<>();
                result.put(alias.contactId, contactAliases);
            }
            contactAliases.add(alias.alias.trim());
        }
        return result;
    }
}
