package com.voicedeck.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A locally configured command, greeting, or filler phrase for speech matching. */
@Entity(
        tableName = "speech_dictionary",
        indices = {
                @Index(
                        value = {"languageTag", "normalizedTerm", "category"},
                        unique = true
                )
        }
)
public class SpeechDictionaryEntry {

    public static final String CATEGORY_COMMAND = "COMMAND";
    public static final String CATEGORY_GREETING = "GREETING";
    public static final String CATEGORY_FILLER = "FILLER";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String term;

    /** Normalized form used for matching; normalization rules live in the speech layer. */
    @NonNull
    public String normalizedTerm;

    /** BCP-47 language tag, for example en-IN, hi-IN, or mr-IN. */
    @NonNull
    public String languageTag;

    @NonNull
    public String category;

    public boolean enabled;

    public SpeechDictionaryEntry(
            @NonNull String term,
            @NonNull String normalizedTerm,
            @NonNull String languageTag,
            @NonNull String category,
            boolean enabled
    ) {
        this.term = term;
        this.normalizedTerm = normalizedTerm;
        this.languageTag = languageTag;
        this.category = category;
        this.enabled = enabled;
    }
}
