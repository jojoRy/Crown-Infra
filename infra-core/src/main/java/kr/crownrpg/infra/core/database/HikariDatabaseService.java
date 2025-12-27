package kr.crownrpg.infra.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.crownrpg.infra.api.database.DatabaseConfig;
import kr.crownrpg.infra.api.database.DatabaseService;
import kr.crownrpg.infra.api.database.DbSession;
import kr.crownrpg.infra.api.database.TransactionCallback;
import kr.crownrpg.infra.api.database.TransactionVoidCallback;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MySQL DatabaseService implementation backed by HikariCP and JDBC.
 */
public class HikariDatabaseService implements DatabaseService {

    private final DatabaseConfig config;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private HikariDataSource dataSource;

    public HikariDatabaseService(DatabaseConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        ensureStarted();
        Objects.requireNonNull(callback, "callback");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            DbSession session = new JdbcDbSession(connection);
            try {
                T result = callback.doInTransaction(session);
                connection.commit();
                return result;
            } catch (Exception e) {
                rollbackQuietly(connection);
                throw new RuntimeException("Transaction failed", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to obtain connection", e);
        }
    }

    @Override
    public void executeVoid(TransactionVoidCallback callback) {
        ensureStarted();
        Objects.requireNonNull(callback, "callback");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            DbSession session = new JdbcDbSession(connection);
            try {
                callback.doInTransaction(session);
                connection.commit();
            } catch (Exception e) {
                rollbackQuietly(connection);
                throw new RuntimeException("Transaction failed", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to obtain connection", e);
        }
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public void start() {
        if (stopped.get()) {
            throw new IllegalStateException("DatabaseService has been stopped and cannot be restarted");
        }
        if (started.get()) {
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            HikariConfig hikariConfig = new HikariConfig();
            int poolSize = Math.max(1, config.poolSize());
            hikariConfig.setJdbcUrl(buildJdbcUrl());
            hikariConfig.setUsername(config.username());
            hikariConfig.setPassword(config.password());
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setMaximumPoolSize(poolSize);
            hikariConfig.setMinimumIdle(Math.min(2, poolSize));
            hikariConfig.setPoolName("InfraCoreHikariPool");
            hikariConfig.setConnectionTimeout(Duration.ofSeconds(30).toMillis());
            hikariConfig.setAutoCommit(false);

            for (Map.Entry<String, String> entry : config.properties().entrySet()) {
                hikariConfig.addDataSourceProperty(entry.getKey(), entry.getValue());
            }

            dataSource = new HikariDataSource(hikariConfig);
        } catch (Exception e) {
            started.set(false);
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
            }
            throw new IllegalStateException("Failed to start DatabaseService", e);
        }
    }

    @Override
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        stopped.set(true);
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private String buildJdbcUrl() {
        String host = config.host();
        int port = config.port();
        String database = config.database();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("host must be provided");
        }
        if (database == null || database.isBlank()) {
            throw new IllegalStateException("database must be provided");
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    private void ensureStarted() {
        if (!started.get()) {
            throw new IllegalStateException("DatabaseService has not been started");
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // ignore
        }
    }
}
