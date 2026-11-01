package com.ajinkyadube.voicedeck.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.ajinkyadube.voicedeck.data.local.dao.SpeechDictionaryDao;
import com.ajinkyadube.voicedeck.data.local.entity.SpeechDictionaryEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SpeechDictionaryDaoTest {

    private VoiceDeckDatabase database;
    private SpeechDictionaryDao dao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = androidx.room.Room.inMemoryDatabaseBuilder(
                context,
                VoiceDeckDatabase.class
        ).allowMainThreadQueries().build();
        dao = database.speechDictionaryDao();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void migrationFromVersionOnePreservesRowsAndMarksEssentialDefaults() {
        Context context = ApplicationProvider.getApplicationContext();
        String databaseName = "voicedeck-migration-test.db";
        context.deleteDatabase(databaseName);

        SQLiteOpenHelper legacy = new SQLiteOpenHelper(context, databaseName, null, 1) {
            @Override public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE speech_dictionary (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "term TEXT NOT NULL, normalizedTerm TEXT NOT NULL, " +
                        "languageTag TEXT NOT NULL, category TEXT NOT NULL, " +
                        "enabled INTEGER NOT NULL)");
                db.execSQL("CREATE UNIQUE INDEX index_speech_dictionary_languageTag_normalizedTerm_category " +
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
                .addMigrations(VoiceDeckDatabase.MIGRATION_1_2, VoiceDeckDatabase.MIGRATION_2_3)
                .allowMainThreadQueries()
                .build();
        try {
            List<SpeechDictionaryEntry> entries = migrated.speechDictionaryDao()
                    .getEntriesForLanguage("en-IN");
            assertEquals(1, entries.size());
            assertEquals("call", entries.get(0).normalizedTerm);
            assertTrue(entries.get(0).isDefault);
            assertTrue(entries.get(0).isProtected);
        } finally {
            migrated.close();
            context.deleteDatabase(databaseName);
        }
    }

    @Test
    public void insertsAndLoadsEnabledEntriesForLanguage() {
        dao.insert(new SpeechDictionaryEntry(
                "call", "call", "en-IN",
                SpeechDictionaryEntry.CATEGORY_COMMAND, true
        ));
        dao.insert(new SpeechDictionaryEntry(
                "please", "please", "en-IN",
                SpeechDictionaryEntry.CATEGORY_FILLER, false
        ));
        dao.insert(new SpeechDictionaryEntry(
                "namaste", "namaste", "hi-IN",
                SpeechDictionaryEntry.CATEGORY_GREETING, true
        ));

        List<SpeechDictionaryEntry> entries = dao.getEnabledEntries("en-IN");

        assertEquals(1, entries.size());
        assertEquals("call", entries.get(0).normalizedTerm);
    }

    @Test
    public void protectedDefaultCommandCannotBeDisabledOrDeleted() {
        SpeechDictionaryEntry protectedEntry = new SpeechDictionaryEntry(
                "call", "call", "en-IN", SpeechDictionaryEntry.CATEGORY_COMMAND,
                true, true, true
        );
        long id = dao.insert(protectedEntry);

        assertTrue(id > 0);
        assertEquals(0, dao.setEnabled(id, false));
        assertEquals(0, dao.deleteCustomEntry(id));
        assertEquals(1, dao.setEnabled(id, true));
        assertEquals(1, dao.getEnabledEntries("en-IN").size());
    }

    @Test
    public void customEntryCanBeDisabledAndDeleted() {
        SpeechDictionaryEntry customEntry = new SpeechDictionaryEntry(
                "ring my friend", "ring my friend", "en-IN",
                SpeechDictionaryEntry.CATEGORY_COMMAND, true, false, false
        );
        long id = dao.insert(customEntry);

        assertTrue(id > 0);
        assertEquals(1, dao.setEnabled(id, false));
        assertEquals(0, dao.getEnabledEntries("en-IN").size());
        assertEquals(1, dao.deleteCustomEntry(id));
        assertTrue(dao.getEntriesForLanguage("en-IN").isEmpty());
    }

    @Test
    public void restoreDefaultsReEnablesOnlyBundledEntries() {
        SpeechDictionaryEntry defaultEntry = new SpeechDictionaryEntry(
                "call", "call", "en-IN", SpeechDictionaryEntry.CATEGORY_COMMAND,
                false, true, true
        );
        SpeechDictionaryEntry customEntry = new SpeechDictionaryEntry(
                "ring my friend", "ring my friend", "en-IN",
                SpeechDictionaryEntry.CATEGORY_COMMAND, false, false, false
        );
        dao.insert(defaultEntry);
        dao.insert(customEntry);

        assertEquals(1, dao.enableDefaultEntries("en-IN"));
        List<SpeechDictionaryEntry> all = dao.getEntriesForLanguage("en-IN");
        assertEquals(2, all.size());
        assertTrue(all.get(0).enabled || all.get(1).enabled);
        assertTrue(!all.get(0).enabled || !all.get(1).enabled);
    }

    @Test
    public void preventsDuplicateNormalizedTermsWithinLanguageAndCategory() {
        SpeechDictionaryEntry first = new SpeechDictionaryEntry(
                "Call", "call", "en-IN",
                SpeechDictionaryEntry.CATEGORY_COMMAND, true
        );
        SpeechDictionaryEntry duplicate = new SpeechDictionaryEntry(
                "call", "call", "en-IN",
                SpeechDictionaryEntry.CATEGORY_COMMAND, true
        );

        long firstId = dao.insert(first);
        long duplicateId = dao.insert(duplicate);

        assertTrue(firstId > 0);
        assertEquals(-1L, duplicateId);
        assertEquals(1, dao.getEnabledEntries("en-IN").size());
    }
}
