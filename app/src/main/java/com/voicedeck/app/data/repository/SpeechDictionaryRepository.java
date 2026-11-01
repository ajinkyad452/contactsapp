package com.voicedeck.app.data.repository;

import com.voicedeck.app.data.local.VoiceDeckDatabase;
import com.voicedeck.app.data.local.dao.SpeechDictionaryDao;
import com.voicedeck.app.data.local.entity.SpeechDictionaryEntry;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * App-facing asynchronous access to speech dictionary data.
 * The caller supplies executors so this repository has no Activity or UI dependency.
 */
public final class SpeechDictionaryRepository {

    private final SpeechDictionaryDao dao;
    private final Executor databaseExecutor;
    private final Executor callbackExecutor;

    public SpeechDictionaryRepository(
            VoiceDeckDatabase database,
            Executor databaseExecutor,
            Executor callbackExecutor
    ) {
        this.dao = Objects.requireNonNull(database, "database").speechDictionaryDao();
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
    }

    public void getEnabledEntries(String languageTag, ResultCallback<List<SpeechDictionaryEntry>> callback) {
        execute(() -> dao.getEnabledEntries(languageTag), callback);
    }

    public void getEntriesForLanguage(String languageTag,
                                      ResultCallback<List<SpeechDictionaryEntry>> callback) {
        execute(() -> dao.getEntriesForLanguage(languageTag), callback);
    }

    public void getAllEnabledEntries(ResultCallback<List<SpeechDictionaryEntry>> callback) {
        execute(dao::getAllEnabledEntries, callback);
    }

    public void insert(SpeechDictionaryEntry entry, ResultCallback<Long> callback) {
        execute(() -> dao.insert(entry), callback);
    }

    public void insertAll(List<SpeechDictionaryEntry> entries, ResultCallback<List<Long>> callback) {
        execute(() -> dao.insertAll(entries), callback);
    }

    public void setEnabled(long id, boolean enabled, ResultCallback<Integer> callback) {
        execute(() -> dao.setEnabled(id, enabled), callback);
    }

    public void deleteCustomEntry(long id, ResultCallback<Integer> callback) {
        execute(() -> dao.deleteCustomEntry(id), callback);
    }

    /** Re-seeds missing bundled terms and re-enables defaults for one language. */
    public void restoreDefaults(String languageTag,
                                List<SpeechDictionaryEntry> defaults,
                                ResultCallback<Integer> callback) {
        execute(() -> {
            dao.insertAll(defaults);
            return dao.enableDefaultEntries(languageTag);
        }, callback);
    }

    private <T> void execute(Callable<T> operation, ResultCallback<T> callback) {
        Objects.requireNonNull(callback, "callback");
        databaseExecutor.execute(() -> {
            try {
                T result = operation.call();
                callbackExecutor.execute(() -> callback.onSuccess(result));
            } catch (Exception exception) {
                callbackExecutor.execute(() -> callback.onError(exception));
            }
        });
    }

    public interface ResultCallback<T> {
        void onSuccess(T result);

        void onError(Exception exception);
    }
}
