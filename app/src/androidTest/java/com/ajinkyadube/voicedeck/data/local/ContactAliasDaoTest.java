package com.ajinkyadube.voicedeck.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.ajinkyadube.voicedeck.ContactAliasMatcherMap;
import com.ajinkyadube.voicedeck.ContactItem;
import com.ajinkyadube.voicedeck.ContactNameMatcher;
import com.ajinkyadube.voicedeck.data.local.dao.ContactAliasDao;
import com.ajinkyadube.voicedeck.data.local.entity.ContactAlias;
import com.ajinkyadube.voicedeck.speech.SpeechCommandFilter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ContactAliasDaoTest {

    private VoiceDeckDatabase database;
    private ContactAliasDao dao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = androidx.room.Room.inMemoryDatabaseBuilder(
                context, VoiceDeckDatabase.class).allowMainThreadQueries().build();
        dao = database.contactAliasDao();
    }

    @After
    public void tearDown() {
        if (database != null) database.close();
    }

    @Test
    public void oneContactCanHaveMultipleAliases() {
        assertTrue(dao.insert(alias(10, "Aai", "aai", "mr-IN")) > 0);
        assertTrue(dao.insert(alias(10, "Mummy", "mummy", "en-IN")) > 0);
        assertTrue(dao.insert(alias(10, "Mom", "mom", "en-IN")) > 0);

        assertEquals(3, dao.getAliasesForContact(10).size());
    }

    @Test
    public void sameAliasCanBelongToDifferentContactsAndMustRemainAmbiguous() {
        assertTrue(dao.insert(alias(10, "Rahul", "rahul", "en-IN")) > 0);
        assertTrue(dao.insert(alias(20, "Rahul", "rahul", "en-IN")) > 0);

        List<ContactAlias> matches = dao.findByNormalizedAlias("rahul", "en-IN");
        assertEquals(2, matches.size());
        assertEquals(10, matches.get(0).contactId);
        assertEquals(20, matches.get(1).contactId);
    }

    @Test
    public void preventsDuplicateAliasForSameContactAndLanguage() {
        assertTrue(dao.insert(alias(10, "Rahul", "rahul", "en-IN")) > 0);
        assertEquals(-1L, dao.insert(alias(10, "RAHUL", "rahul", "en-IN")));
        assertEquals(1, dao.getAliasesForContact(10).size());
    }

    @Test
    public void sameNormalizedAliasCanBeStoredForDifferentLanguages() {
        assertTrue(dao.insert(alias(10, "Aai", "aai", "en-IN")) > 0);
        assertTrue(dao.insert(alias(10, "Aai", "aai", "mr-IN")) > 0);
        assertEquals(1, dao.getAliasesForLanguage("en-IN").size());
        assertEquals(1, dao.getAliasesForLanguage("mr-IN").size());
    }

    @Test
    public void canUpdateAliasTextContactAndLanguageWithoutChangingItsId() {
        long id = dao.insert(alias(10, "Aai", "aai", "mr-IN"));
        ContactAlias updated = alias(20, "Mummy", "mummy", "en-IN");
        updated.id = id;

        assertEquals(1, dao.update(updated));
        ContactAlias saved = dao.getAllAliases().get(0);
        assertEquals(id, saved.id);
        assertEquals(20L, saved.contactId);
        assertEquals("Mummy", saved.alias);
        assertEquals("en-IN", saved.languageTag);
    }

    @Test
    public void updateIgnoresDuplicateAliasForSameContactAndLanguage() {
        dao.insert(alias(10, "Aai", "aai", "mr-IN"));
        long secondId = dao.insert(alias(10, "Mummy", "mummy", "mr-IN"));
        ContactAlias duplicate = alias(10, "आई", "aai", "mr-IN");
        duplicate.id = secondId;

        assertEquals(0, dao.update(duplicate));
        assertEquals(2, dao.getAllAliases().size());
    }

    @Test
    public void canDeleteOneAliasOrAllAliasesForAContact() {
        long firstId = dao.insert(alias(10, "Aai", "aai", "mr-IN"));
        dao.insert(alias(10, "Mummy", "mummy", "en-IN"));
        dao.insert(alias(20, "Rahul", "rahul", "en-IN"));

        assertEquals(1, dao.deleteById(firstId));
        assertEquals(1, dao.deleteAliasesForContact(10));
        assertEquals(1, dao.getAllAliases().size());
    }

    @Test
    public void roomAliasesFlowIntoMatcherAndPreserveDuplicateAliasAmbiguity() {
        dao.insert(alias(10, "Rahul", "rahul", "en-IN"));
        dao.insert(alias(20, "Rahul", "rahul", "en-IN"));
        dao.insert(alias(10, "आई", "आई", "mr-IN"));

        List<ContactAlias> englishAliases = dao.getAliasesForLanguage("en-IN");
        List<ContactAlias> marathiAliases = dao.getAliasesForLanguage("mr-IN");
        ContactItem first = contact(10, "Rahul Sharma");
        ContactItem second = contact(20, "Amit Desai");
        ContactNameMatcher matcher = new ContactNameMatcher(
                Arrays.asList(first, second),
                ContactAliasMatcherMap.fromAliases(englishAliases,
                        new HashSet<>(Arrays.asList(10L, 20L))));

        ContactNameMatcher.Result result = matcher.resolve(
                "Call Rahul", new SpeechCommandFilter(Collections.singletonList("call")));

        assertEquals(ContactNameMatcher.Status.MULTIPLE_MATCHES, result.getStatus());
        assertEquals(2, result.getMatches().size());
        assertEquals(1, marathiAliases.size());

        // A stored alias for a contact absent from the current Contacts Provider list is ignored.
        Map<Long, List<String>> filtered = ContactAliasMatcherMap.fromAliases(
                englishAliases, Collections.singleton(10L));
        assertEquals(Collections.singletonList("Rahul"), filtered.get(10L));
        assertTrue(!filtered.containsKey(20L));
    }

    @Test
    public void migrationFromVersionTwoCreatesAliasTableAndPreservesDictionary() {
        Context context = ApplicationProvider.getApplicationContext();
        String databaseName = "voicedeck-alias-migration-test.db";
        context.deleteDatabase(databaseName);

        SQLiteOpenHelper legacy = new SQLiteOpenHelper(context, databaseName, null, 2) {
            @Override public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE speech_dictionary (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "term TEXT NOT NULL, normalizedTerm TEXT NOT NULL, " +
                        "languageTag TEXT NOT NULL, category TEXT NOT NULL, " +
                        "enabled INTEGER NOT NULL, isDefault INTEGER NOT NULL DEFAULT 1, " +
                        "isProtected INTEGER NOT NULL DEFAULT 0)");
                db.execSQL("CREATE UNIQUE INDEX " +
                        "index_speech_dictionary_languageTag_normalizedTerm_category " +
                        "ON speech_dictionary(languageTag, normalizedTerm, category)");
                db.execSQL("INSERT INTO speech_dictionary " +
                        "(term, normalizedTerm, languageTag, category, enabled) " +
                        "VALUES ('call', 'call', 'en-IN', 'COMMAND', 1)");
            }
            @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
        };
        legacy.getWritableDatabase().close();
        legacy.close();

        VoiceDeckDatabase migrated = androidx.room.Room.databaseBuilder(
                context, VoiceDeckDatabase.class, databaseName)
                .addMigrations(VoiceDeckDatabase.MIGRATION_1_2,
                        VoiceDeckDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .build();
        try {
            assertEquals(1, migrated.speechDictionaryDao().getEntriesForLanguage("en-IN").size());
            assertTrue(migrated.contactAliasDao().getAllAliases().isEmpty());
            assertTrue(migrated.contactAliasDao().insert(
                    alias(42, "Aai", "aai", "mr-IN")) > 0);
        } finally {
            migrated.close();
            context.deleteDatabase(databaseName);
        }
    }

    private static ContactItem contact(long id, String name) {
        ContactItem item = new ContactItem();
        item.id = id;
        item.name = name;
        return item;
    }

    private static ContactAlias alias(long contactId, String alias,
                                      String normalizedAlias, String languageTag) {
        return new ContactAlias(contactId, alias, normalizedAlias, languageTag, 123456789L);
    }
}
