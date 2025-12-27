package kr.crownrpg.infra.core.database;

import kr.crownrpg.infra.api.database.ConnectionProvider;
import kr.crownrpg.infra.api.database.DatabaseClient;
import kr.crownrpg.infra.api.database.DatabaseException;
import kr.crownrpg.infra.api.database.QueryExecutor;
import kr.crownrpg.infra.api.database.Transaction;

import java.sql.Connection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * DatabaseClient 구현체.
 *
 * - query(...) : 커넥션 1회 사용 후 종료
 * - transaction(...) : setAutoCommit(false) 트랜잭션 스코프 보장
 */
public final class JdbcDatabaseClient implements DatabaseClient {

    private final ConnectionProvider provider;
    private final Executor executor;

    private volatile boolean closed = false;

    public JdbcDatabaseClient(ConnectionProvider provider, Executor executor) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public <T> CompletableFuture<T> query(Function<QueryExecutor, T> action) {
        ensureOpen();
        Objects.requireNonNull(action, "action");

        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = provider.getConnection()) {
                JdbcQueryExecutor exec = new JdbcQueryExecutor(c);
                return action.apply(exec);
            } catch (Exception e) {
                throw wrap(e, "Database query failed");
            }
        }, executor);
    }

    @Override
    public <T> CompletableFuture<T> transaction(Function<Transaction, T> action) {
        ensureOpen();
        Objects.requireNonNull(action, "action");

        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = provider.getConnection()) {
                c.setAutoCommit(false);

                JdbcTransaction tx = new JdbcTransaction(c);
                try {
                    T result = action.apply(tx);

                    // 유저가 rollback() 호출했으면 rollback
                    if (tx.isRollbackOnly()) {
                        tx.rollback();
                    } else {
                        tx.commit();
                    }
                    return result;

                } catch (Throwable userEx) {
                    try {
                        tx.rollback();
                    } catch (Throwable ignore) {}
                    throw userEx;

                } finally {
                    try {
                        c.setAutoCommit(true);
                    } catch (Throwable ignore) {}
                }
            } catch (Exception e) {
                throw wrap(e, "Database transaction failed");
            }
        }, executor);
    }

    @Override
    public void close() {
        closed = true;
        try {
            provider.close();
        } catch (Throwable t) {
            // 종료 중 예외는 크게 문제 삼지 않음
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("DatabaseClient is closed");
    }

    private static DatabaseException wrap(Throwable t, String msg) {
        if (t instanceof DatabaseException de) return de;
        return new DatabaseException(msg, t);
    }
}
