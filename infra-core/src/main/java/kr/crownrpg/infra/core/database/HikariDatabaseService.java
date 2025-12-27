package kr.crownrpg.infra.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.crownrpg.infra.api.database.DatabaseConfig;
import kr.crownrpg.infra.api.database.DatabaseException;
import kr.crownrpg.infra.api.database.DatabaseService;
import kr.crownrpg.infra.api.database.DatabaseState;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MySQL DatabaseService implementation backed by HikariCP and JDBC.
 */
public class HikariDatabaseService implements DatabaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HikariDatabaseService.class);
    private static final long CONNECTION_WAIT_WARN_MS = 1000;
    private static final long TRANSACTION_WARN_MS = 2000;
    private static final int FAILURE_THRESHOLD = 3;

    private final DatabaseConfig config;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicReference<DatabaseState> state = new AtomicReference<>(DatabaseState.STOPPED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final ScheduledExecutorService healthChecker;
    private final AtomicBoolean recoveryLoopStarted = new AtomicBoolean(false);
    private HikariDataSource dataSource;

    public HikariDatabaseService(DatabaseConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.healthChecker = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("hikari-db-health");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        ensureAvailable();
        Objects.requireNonNull(callback, "callback");
        long waitStart = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            long waitMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStart);
            logConnectionWait(waitMs);
            connection.setAutoCommit(false);
            DbSession session = new JdbcDbSession(connection);
            try {
                long txStart = System.nanoTime();
                T result = callback.doInTransaction(session);
                connection.commit();
                logTransactionDuration(txStart);
                consecutiveFailures.set(0);
                return result;
            } catch (Exception e) {
                rollbackQuietly(connection);
                markFailure("트랜잭션 실패", e);
                throw new DatabaseException("Transaction failed", e);
            }
        } catch (SQLException e) {
            markFailure("커넥션 획득 실패", e);
            throw new DatabaseException("Failed to obtain connection", e);
        }
    }

    @Override
    public void executeVoid(TransactionVoidCallback callback) {
        ensureAvailable();
        Objects.requireNonNull(callback, "callback");
        long waitStart = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            long waitMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStart);
            logConnectionWait(waitMs);
            connection.setAutoCommit(false);
            DbSession session = new JdbcDbSession(connection);
            try {
                long txStart = System.nanoTime();
                callback.doInTransaction(session);
                connection.commit();
                logTransactionDuration(txStart);
                consecutiveFailures.set(0);
            } catch (Exception e) {
                rollbackQuietly(connection);
                markFailure("트랜잭션 실패", e);
                throw new DatabaseException("Transaction failed", e);
            }
        } catch (SQLException e) {
            markFailure("커넥션 획득 실패", e);
            throw new DatabaseException("Failed to obtain connection", e);
        }
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    @Override
    public DatabaseState state() {
        return state.get();
    }

    @Override
    public void start() {
        if (stopped.get()) {
            LOGGER.warn("DatabaseService가 완전히 중단되어 재시작할 수 없습니다");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        transitionState(DatabaseState.CONNECTING, "데이터베이스 풀을 초기화합니다");
        if (initializeDataSource()) {
            transitionState(DatabaseState.RUNNING, "데이터베이스 연결이 준비되었습니다");
        } else {
            transitionState(DatabaseState.DEGRADED, "데이터베이스 초기화에 실패했습니다 - 자동 복구를 대기합니다");
        }
        startRecoveryLoop();
    }

    @Override
    public void stop() {
        if (!started.getAndSet(false)) {
            return;
        }
        stopped.set(true);
        transitionState(DatabaseState.STOPPED, "데이터베이스 서비스를 종료합니다");
        healthChecker.shutdownNow();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private boolean initializeDataSource() {
        try {
            if (dataSource != null) {
                dataSource.close();
            }
            HikariConfig hikariConfig = new HikariConfig();
            int poolSize = Math.max(1, config.poolSize());
            hikariConfig.setJdbcUrl(buildJdbcUrl());
            hikariConfig.setUsername(config.username());
            hikariConfig.setPassword(config.password());
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setMaximumPoolSize(poolSize);
            hikariConfig.setMinimumIdle(Math.min(2, poolSize));
            hikariConfig.setPoolName("InfraCoreHikariPool");
            hikariConfig.setConnectionTimeout(Duration.ofSeconds(5).toMillis());
            hikariConfig.setValidationTimeout(Duration.ofSeconds(3).toMillis());
            hikariConfig.setMaxLifetime(Duration.ofMinutes(30).toMillis());
            hikariConfig.setLeakDetectionThreshold(Duration.ofSeconds(10).toMillis());
            hikariConfig.setAutoCommit(false);

            for (Map.Entry<String, String> entry : config.properties().entrySet()) {
                hikariConfig.addDataSourceProperty(entry.getKey(), entry.getValue());
            }

            dataSource = new HikariDataSource(hikariConfig);
            consecutiveFailures.set(0);
            return true;
        } catch (Exception e) {
            LOGGER.warn("HikariDataSource 초기화 실패", e);
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
            }
            return false;
        }
    }

    private void ensureAvailable() {
        if (state.get() != DatabaseState.RUNNING || dataSource == null) {
            throw new DatabaseException("데이터베이스가 DEGRADED 상태입니다");
        }
    }

    private void startRecoveryLoop() {
        if (recoveryLoopStarted.compareAndSet(false, true)) {
            healthChecker.scheduleWithFixedDelay(this::attemptRecovery, 2, 5, TimeUnit.SECONDS);
        }
    }

    private void attemptRecovery() {
        if (stopped.get()) {
            return;
        }
        DatabaseState current = state.get();
        if (current == DatabaseState.RUNNING) {
            return;
        }
        if (initializeDataSource()) {
            transitionState(DatabaseState.RUNNING, "데이터베이스 연결이 복구되었습니다");
        }
    }

    private void markFailure(String message, Exception cause) {
        int failures = consecutiveFailures.incrementAndGet();
        LOGGER.warn("{} (연속 {}회)", message, failures, cause);
        if (failures >= FAILURE_THRESHOLD) {
            transitionState(DatabaseState.DEGRADED, "데이터베이스 장애 감지 - 요청을 거부합니다");
        }
    }

    private void logConnectionWait(long waitMs) {
        if (waitMs > CONNECTION_WAIT_WARN_MS) {
            LOGGER.warn("DB 커넥션 획득 지연 {}ms - 풀 설정을 점검하세요", waitMs);
        } else {
            LOGGER.info("DB 커넥션 획득 {}ms", waitMs);
        }
    }

    private void logTransactionDuration(long txStart) {
        long txDuration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - txStart);
        if (txDuration > TRANSACTION_WARN_MS) {
            LOGGER.warn("트랜잭션이 {}ms 소요되었습니다 - 쿼리 분할 또는 배치를 고려하세요", txDuration);
        } else {
            LOGGER.info("트랜잭션 완료 ({}ms)", txDuration);
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

    private void transitionState(DatabaseState newState, String message) {
        DatabaseState previous = state.getAndSet(newState);
        if (previous == newState) {
            return;
        }
        switch (newState) {
            case RUNNING -> LOGGER.info(message);
            case CONNECTING -> LOGGER.info(message);
            case DEGRADED -> LOGGER.warn(message);
            case STOPPED -> LOGGER.info(message);
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
