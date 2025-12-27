package kr.crownrpg.infra.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.crownrpg.infra.api.database.DatabaseConfig;
import kr.crownrpg.infra.api.database.DatabaseService;
import kr.crownrpg.infra.api.database.DbSession;
import kr.crownrpg.infra.api.database.TransactionCallback;
import kr.crownrpg.infra.api.database.TransactionVoidCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

/**
 * MySQL DatabaseService implementation backed by HikariCP and JDBC.
 */
public class HikariDatabaseService implements DatabaseService {

    // ⚠️ 플러그인별 테이블은 접두사/네임스페이스를 강제해 충돌을 예방한다.
    // TODO: 필요 시 멀티 DataSource를 주입받을 수 있도록 확장 포인트를 추가한다.
    private static final Logger LOGGER = LoggerFactory.getLogger(HikariDatabaseService.class);
    private static final long CONNECTION_WAIT_WARN_MS = 1000;
    private static final long TRANSACTION_WARN_MS = 2000;

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
        long waitStart = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            long waitMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStart);
            if (waitMs > CONNECTION_WAIT_WARN_MS) {
                LOGGER.warn("DB 커넥션 획득 지연 {}ms - 풀 설정을 점검하세요", waitMs);
            } else {
                LOGGER.info("DB 커넥션 획득 {}ms", waitMs);
            }
            connection.setAutoCommit(false);
            DbSession session = new JdbcDbSession(connection);
            try {
                long txStart = System.nanoTime();
                // ⚠️ 긴 트랜잭션 금지: 게임 틱 지연을 피하기 위해 콜백 내부는 빠르게 종료되어야 함.
                T result = callback.doInTransaction(session);
                connection.commit();
                long txDuration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - txStart);
                if (txDuration > TRANSACTION_WARN_MS) {
                    LOGGER.warn("트랜잭션이 {}ms 소요되었습니다 - 쿼리 분할 또는 배치를 고려하세요", txDuration);
                } else {
                    LOGGER.info("트랜잭션 완료 ({}ms)", txDuration);
                }
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
        long waitStart = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            long waitMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStart);
            if (waitMs > CONNECTION_WAIT_WARN_MS) {
                LOGGER.warn("DB 커넥션 획득 지연 {}ms - 풀 설정을 점검하세요", waitMs);
            } else {
                LOGGER.info("DB 커넥션 획득 {}ms", waitMs);
            }
            connection.setAutoCommit(false);
            DbSession session = new JdbcDbSession(connection);
            try {
                long txStart = System.nanoTime();
                // ⚠️ 긴 트랜잭션 금지: 게임 서버 메인 스레드를 블록하지 않도록 짧게 유지.
                callback.doInTransaction(session);
                connection.commit();
                long txDuration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - txStart);
                if (txDuration > TRANSACTION_WARN_MS) {
                    LOGGER.warn("트랜잭션이 {}ms 소요되었습니다 - 쿼리/배치를 재검토하세요", txDuration);
                } else {
                    LOGGER.info("트랜잭션 완료 ({}ms)", txDuration);
                }
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
