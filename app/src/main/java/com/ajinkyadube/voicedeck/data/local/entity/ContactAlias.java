package com.ajinkyadube.voicedeck.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A user-defined spoken name that points to an Android Contacts Provider contact. */
@Entity(
        tableName = "contact_aliases",
        indices = {
                @Index(
                        name = "index_contact_aliases_contactId_normalizedAlias_languageTag",
                        value = {"contactId", "normalizedAlias", "languageTag"},
                        unique = true
                ),
                @Index(
                        name = "index_contact_aliases_normalizedAlias_languageTag",
                        value = {"normalizedAlias", "languageTag"}
                )
        }
)
public class ContactAlias {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** ID from ContactsContract.Contacts._ID; the contact itself remains in Android Contacts. */
    @ColumnInfo(name = "contactId")
    public long contactId;

    @NonNull
    public String alias;

    /** Pre-normalized alias used for local matching. */
    @NonNull
    public String normalizedAlias;

    /** BCP-47 language tag for the language in which this alias is spoken. */
    @NonNull
    public String languageTag;

    /** Unix epoch milliseconds when this alias was created. */
    public long createdAt;

    public ContactAlias(
            long contactId,
            @NonNull String alias,
            @NonNull String normalizedAlias,
            @NonNull String languageTag,
            long createdAt
    ) {
        this.contactId = contactId;
        this.alias = alias;
        this.normalizedAlias = normalizedAlias;
        this.languageTag = languageTag;
        this.createdAt = createdAt;
    }
}
