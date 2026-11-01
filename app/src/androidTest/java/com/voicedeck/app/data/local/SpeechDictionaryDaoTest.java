package com.voicedeck.app.data.local;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.voicedeck.app.data.local.dao.SpeechDictionaryDao;
import com.voicedeck.app.data.local.entity.SpeechDictionaryEntry;

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
