package com.voicedeck.app.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.voicedeck.app.data.local.dao.SpeechDictionaryDao;
import com.voicedeck.app.data.local.entity.SpeechDictionaryEntry;

@Database(
        entities = {SpeechDictionaryEntry.class},
        version = 2,
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

    public abstract SpeechDictionaryDao speechDictionaryDao();

    public static VoiceDeckDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (VoiceDeckDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            VoiceDeckDatabase.class,
                            "voicedeck.db"
                    ).addMigrations(MIGRATION_1_2).build();
                }
            }
        }
        return INSTANCE;
    }
}
