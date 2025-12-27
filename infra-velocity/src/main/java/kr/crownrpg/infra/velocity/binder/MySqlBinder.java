package kr.crownrpg.infra.velocity.binder;

import kr.crownrpg.infra.api.database.DatabaseClient;
import kr.crownrpg.infra.core.database.HikariDatabaseProvider;

import java.io.Closeable;
import java.util.Map;
import java.util.Objects;

public final class MySqlBinder implements Closeable {

    private final HikariDatabaseProvider provider = new HikariDatabaseProvider();
    private volatile DatabaseClient client;

    public void start(Map<String, Object> dbConfig) {
        Objects.requireNonNull(dbConfig, "dbConfig");
        provider.initialize(dbConfig);
        this.client = provider.getDatabaseClient();
    }

    public DatabaseClient client() {
        DatabaseClient c = client;
        if (c == null) throw new IllegalStateException("MySqlBinder가 아직 시작되지 않았습니다.");
        return c;
    }

    @Override
    public void close() {
        provider.shutdown();
        client = null;
    }
}
