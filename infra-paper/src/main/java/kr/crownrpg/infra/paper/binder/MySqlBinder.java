package kr.crownrpg.infra.paper.binder;

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
        if (c == null) throw new IllegalStateException("MySqlBinder not started");
        return c;
    }

    @Override
    public void close() {
        provider.shutdown();
        client = null;
    }
}
