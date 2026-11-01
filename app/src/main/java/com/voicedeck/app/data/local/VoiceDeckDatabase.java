package com.voicedeck.app.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.voicedeck.app.data.local.dao.SpeechDictionaryDao;
import com.voicedeck.app.data.local.entity.SpeechDictionaryEntry;

@Database(
        entities = {SpeechDictionaryEntry.class},
        version = 1,
        exportSchema = true
)
public abstract class VoiceDeckDatabase extends RoomDatabase {

    private static volatile VoiceDeckDatabase INSTANCE;

    public abstract SpeechDictionaryDao speechDictionaryDao();

    public static VoiceDeckDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (VoiceDeckDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            VoiceDeckDatabase.class,
                            "voicedeck.db"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
