package com.voicedeck.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.voicedeck.app.data.local.entity.SpeechDictionaryEntry;

import java.util.List;

@Dao
public interface SpeechDictionaryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(SpeechDictionaryEntry entry);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertAll(List<SpeechDictionaryEntry> entries);

    @Update
    int update(SpeechDictionaryEntry entry);

    @Delete
    int delete(SpeechDictionaryEntry entry);

    @Query("SELECT * FROM speech_dictionary WHERE id = :id LIMIT 1")
    SpeechDictionaryEntry findById(long id);

    @Query("SELECT * FROM speech_dictionary " +
            "WHERE languageTag = :languageTag AND enabled = 1 " +
            "ORDER BY category ASC, normalizedTerm ASC")
    List<SpeechDictionaryEntry> getEnabledEntries(String languageTag);

    @Query("SELECT * FROM speech_dictionary WHERE enabled = 1 " +
            "ORDER BY languageTag ASC, category ASC, normalizedTerm ASC")
    List<SpeechDictionaryEntry> getAllEnabledEntries();
}
