package com.voicedeck.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.voicedeck.app.data.local.entity.ContactAlias;

import java.util.List;

@Dao
public interface ContactAliasDao {

    /** Returns the new row ID, or -1 when this contact already has this normalized alias. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(ContactAlias alias);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertAll(List<ContactAlias> aliases);

    @Query("SELECT * FROM contact_aliases WHERE contactId = :contactId " +
            "ORDER BY languageTag ASC, normalizedAlias ASC")
    List<ContactAlias> getAliasesForContact(long contactId);

    @Query("SELECT * FROM contact_aliases WHERE languageTag = :languageTag " +
            "ORDER BY normalizedAlias ASC, contactId ASC")
    List<ContactAlias> getAliasesForLanguage(String languageTag);

    @Query("SELECT * FROM contact_aliases ORDER BY contactId ASC, languageTag ASC, normalizedAlias ASC")
    List<ContactAlias> getAllAliases();

    /** Deliberately returns every owner; duplicate aliases must be handled as ambiguous. */
    @Query("SELECT * FROM contact_aliases WHERE normalizedAlias = :normalizedAlias " +
            "AND languageTag = :languageTag ORDER BY contactId ASC")
    List<ContactAlias> findByNormalizedAlias(String normalizedAlias, String languageTag);

    @Query("DELETE FROM contact_aliases WHERE id = :id")
    int deleteById(long id);

    @Query("DELETE FROM contact_aliases WHERE contactId = :contactId")
    int deleteAliasesForContact(long contactId);
}
