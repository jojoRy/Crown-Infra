package kr.crownrpg.infra.core.database;

import kr.crownrpg.infra.api.database.ConnectionProvider;
import kr.crownrpg.infra.api.database.DatabaseClient;
import kr.crownrpg.infra.api.database.DatabaseException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * infra-paper/infra-velocity 바인더에서 호출하는 "DB 초기화/종료" 유틸.
 *
 * - initialize(configMap) 한 번 호출
 * - getDatabaseClient() 로 계약(DatabaseClient) 반환
 * - shutdown() 으로 종료
 */
public final class HikariDatabaseProvider {

    private volatile ConnectionProvider connectionProvider;
    private volatile ExecutorService executor;
    private volatile DatabaseClient databaseClient;

    public void initialize(Map<String, Object> config) {
        Objects.requireNonNull(config, "config");

        if (databaseClient != null) {
            throw new IllegalStateException("Database already initialized");
        }

        // Hikari DataSource 구성
        HikariConnectionProvider provider = HikariConnectionProvider.fromConfig(config);

        // DB 작업 전용 스레드풀
        int poolThreads = intOrDefault(config.get("pool-threads"), 4);
        ExecutorService exec = ThreadFactories.fixed(poolThreads, "CrownDB");

        this.connectionProvider = provider;
        this.executor = exec;
        this.databaseClient = new JdbcDatabaseClient(provider, exec);
    }

    public DatabaseClient getDatabaseClient() {
        if (databaseClient == null) {
            throw new IllegalStateException("Database not initialized yet");
        }
        return databaseClient;
    }

    public void shutdown() {
        // DatabaseClient.close() -> provider/exec 정리까지 같이 처리해도 되지만
        // 여기서는 명확히 provider/exec를 안전하게 정리한다.
        DatabaseClient client = this.databaseClient;
        this.databaseClient = null;

        ConnectionProvider provider = this.connectionProvider;
        this.connectionProvider = null;

        ExecutorService exec = this.executor;
        this.executor = null;

        try {
            if (client != null) {
                client.close();
            }
        } catch (Throwable t) {
            // 무시
        }

        try {
            if (provider != null) {
                provider.close();
            }
        } catch (Throwable t) {
            // 무시
        }

        try {
            if (exec != null) {
                exec.shutdownNow();
            }
        } catch (Throwable t) {
            // 무시
        }
    }

    private static int intOrDefault(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }
}
