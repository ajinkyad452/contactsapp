package com.ajinkyadube.voicedeck.data.repository;

import com.ajinkyadube.voicedeck.data.local.VoiceDeckDatabase;
import com.ajinkyadube.voicedeck.data.local.dao.ContactAliasDao;
import com.ajinkyadube.voicedeck.data.local.entity.ContactAlias;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/** App-facing asynchronous access to locally stored contact aliases. */
public final class ContactAliasRepository {

    private final ContactAliasDao dao;
    private final Executor databaseExecutor;
    private final Executor callbackExecutor;

    public ContactAliasRepository(
            VoiceDeckDatabase database,
            Executor databaseExecutor,
            Executor callbackExecutor
    ) {
        this.dao = Objects.requireNonNull(database, "database").contactAliasDao();
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
    }

    public void getAliasesForContact(long contactId,
                                     ResultCallback<List<ContactAlias>> callback) {
        execute(() -> dao.getAliasesForContact(contactId), callback);
    }

    public void getAliasesForLanguage(String languageTag,
                                      ResultCallback<List<ContactAlias>> callback) {
        execute(() -> dao.getAliasesForLanguage(languageTag), callback);
    }

    public void getAllAliases(ResultCallback<List<ContactAlias>> callback) {
        execute(dao::getAllAliases, callback);
    }

    public void findByNormalizedAlias(String normalizedAlias, String languageTag,
                                      ResultCallback<List<ContactAlias>> callback) {
        execute(() -> dao.findByNormalizedAlias(normalizedAlias, languageTag), callback);
    }

    public void insert(ContactAlias alias, ResultCallback<Long> callback) {
        execute(() -> dao.insert(alias), callback);
    }

    public void update(ContactAlias alias, ResultCallback<Integer> callback) {
        execute(() -> dao.update(alias), callback);
    }

    public void deleteById(long id, ResultCallback<Integer> callback) {
        execute(() -> dao.deleteById(id), callback);
    }

    public void deleteAliasesForContact(long contactId, ResultCallback<Integer> callback) {
        execute(() -> dao.deleteAliasesForContact(contactId), callback);
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
