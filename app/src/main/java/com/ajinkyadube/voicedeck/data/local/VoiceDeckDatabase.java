package com.ajinkyadube.voicedeck.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.ajinkyadube.voicedeck.data.local.dao.ContactAliasDao;
import com.ajinkyadube.voicedeck.data.local.dao.SpeechDictionaryDao;
import com.ajinkyadube.voicedeck.data.local.entity.ContactAlias;
import com.ajinkyadube.voicedeck.data.local.entity.SpeechDictionaryEntry;

@Database(
        entities = {SpeechDictionaryEntry.class, ContactAlias.class},
        version = 3,
        exportSchema = true
)
public abstract class VoiceDeckDatabase extends RoomDatabase {

    private static volatile VoiceDeckDatabase INSTANCE;

    /** Adds dictionary ownership/protection metadata without deleting existing entries. */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // All rows in v1 were bundled defaults; future custom rows are explicitly false.
            database.execSQL("ALTER TABLE speech_dictionary " +
                    "ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE speech_dictionary " +
                    "ADD COLUMN isProtected INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE speech_dictionary SET isProtected = 1 " +
                    "WHERE languageTag = 'en-IN' AND normalizedTerm = 'call' " +
                    "AND category = 'COMMAND'");
            database.execSQL("UPDATE speech_dictionary SET isProtected = 1 " +
                    "WHERE languageTag IN ('hi-IN', 'mr-IN') AND normalizedTerm = 'कॉल' " +
                    "AND category = 'COMMAND'");
        }
    };

    /** Adds local contact aliases without changing or deleting existing dictionary data. */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS contact_aliases (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "contactId INTEGER NOT NULL, " +
                    "alias TEXT NOT NULL, " +
                    "normalizedAlias TEXT NOT NULL, " +
                    "languageTag TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_contact_aliases_contactId_normalizedAlias_languageTag " +
                    "ON contact_aliases (contactId, normalizedAlias, languageTag)");
            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "index_contact_aliases_normalizedAlias_languageTag " +
                    "ON contact_aliases (normalizedAlias, languageTag)");
        }
    };

    public abstract SpeechDictionaryDao speechDictionaryDao();

    public abstract ContactAliasDao contactAliasDao();

    public static VoiceDeckDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (VoiceDeckDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            VoiceDeckDatabase.class,
                            "voicedeck.db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build();
                }
            }
        }
        return INSTANCE;
    }
}
