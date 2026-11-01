package com.voicedeck.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.voicedeck.app.data.local.entity.SpeechDictionaryEntry;

import java.util.List;

@Dao
public interface SpeechDictionaryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(SpeechDictionaryEntry entry);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertAll(List<SpeechDictionaryEntry> entries);

    @Query("SELECT * FROM speech_dictionary WHERE id = :id LIMIT 1")
    SpeechDictionaryEntry findById(long id);

    @Query("SELECT * FROM speech_dictionary WHERE languageTag = :languageTag " +
            "ORDER BY category ASC, normalizedTerm ASC")
    List<SpeechDictionaryEntry> getEntriesForLanguage(String languageTag);

    @Query("UPDATE speech_dictionary SET enabled = :enabled " +
            "WHERE id = :id AND (isProtected = 0 OR :enabled = 1)")
    int setEnabled(long id, boolean enabled);

    @Query("DELETE FROM speech_dictionary WHERE id = :id AND isDefault = 0")
    int deleteCustomEntry(long id);

    @Query("UPDATE speech_dictionary SET enabled = 1 " +
            "WHERE languageTag = :languageTag AND isDefault = 1")
    int enableDefaultEntries(String languageTag);

    @Query("SELECT * FROM speech_dictionary " +
            "WHERE languageTag = :languageTag AND enabled = 1 " +
            "ORDER BY category ASC, normalizedTerm ASC")
    List<SpeechDictionaryEntry> getEnabledEntries(String languageTag);

    @Query("SELECT * FROM speech_dictionary WHERE enabled = 1 " +
            "ORDER BY languageTag ASC, category ASC, normalizedTerm ASC")
    List<SpeechDictionaryEntry> getAllEnabledEntries();
}
