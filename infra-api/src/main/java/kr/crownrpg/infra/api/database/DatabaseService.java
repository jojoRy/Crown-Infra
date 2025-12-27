package kr.crownrpg.infra.api.database;

import kr.crownrpg.infra.api.lifecycle.ManagedLifecycle;

/**
 * Provides transaction boundaries for database work.
 */
public interface DatabaseService extends ManagedLifecycle {

    <T> T execute(TransactionCallback<T> callback);

    void executeVoid(TransactionVoidCallback callback);

    boolean isStarted();

    DatabaseState state();

    default boolean isRunning() {
        return state() == DatabaseState.RUNNING;
    }

    default boolean isDegraded() {
        return state() == DatabaseState.DEGRADED;
    }
}
