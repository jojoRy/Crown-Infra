package kr.crownrpg.infra.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.crownrpg.infra.api.database.ConnectionProvider;
import kr.crownrpg.infra.api.database.DatabaseException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

/**
 * HikariCP 기반 ConnectionProvider 구현체.
 *
 * config Map 지원 키:
 * - host (String)
 * - port (int)
 * - database (String)
 * - username (String)
 * - password (String)
 * - max-pool-size (int) [default 10]
 * - min-idle (int) [default 2]
 * - connection-timeout-ms (long) [default 3000]
 * - use-ssl (boolean) [default false]
 * - server-timezone (String) [default "UTC"]
 * - character-encoding (String) [default "utf8"]
 */
public final class HikariConnectionProvider implements ConnectionProvider {

    private final HikariDataSource dataSource;

    private HikariConnectionProvider(HikariDataSource ds) {
        this.dataSource = ds;
    }

    public static HikariConnectionProvider fromConfig(Map<String, Object> config) {
        Objects.requireNonNull(config, "config");

        String host = str(config.get("host"), "127.0.0.1");
        int port = integer(config.get("port"), 3306);
        String database = requireStr(config.get("database"), "database");
        String username = requireStr(config.get("username"), "username");
        String password = str(config.get("password"), "");

        int maxPoolSize = integer(config.get("max-pool-size"), 10);
        int minIdle = integer(config.get("min-idle"), 2);
        long connTimeout = longVal(config.get("connection-timeout-ms"), 3000L);

        boolean useSsl = bool(config.get("use-ssl"), false);
        String timezone = str(config.get("server-timezone"), "UTC");
        String encoding = str(config.get("character-encoding"), "utf8");

        String jdbcUrl = buildJdbcUrl(host, port, database, useSsl, timezone, encoding);

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        hc.setUsername(username);
        hc.setPassword(password);

        hc.setMaximumPoolSize(maxPoolSize);
        hc.setMinimumIdle(minIdle);
        hc.setConnectionTimeout(connTimeout);

        // MySQL 8 권장
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hc.addDataSourceProperty("useServerPrepStmts", "true");

        // 안전
        hc.setPoolName("Crown-Hikari");

        return new HikariConnectionProvider(new HikariDataSource(hc));
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        try {
            dataSource.close();
        } catch (Throwable t) {
            throw new DatabaseException("Failed to close HikariDataSource", t);
        }
    }

    private static String buildJdbcUrl(
            String host,
            int port,
            String database,
            boolean useSsl,
            String timezone,
            String encoding
    ) {
        // MySQL Connector/J 8: useSSL, serverTimezone, characterEncoding 등
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&serverTimezone=" + timezone
                + "&characterEncoding=" + encoding
                + "&useUnicode=true"
                + "&allowPublicKeyRetrieval=true";
    }

    private static String requireStr(Object v, String key) {
        String s = str(v, null);
        if (s == null || s.isBlank()) {
            throw new DatabaseException("Missing required database config: " + key);
        }
        return s;
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        return String.valueOf(v);
    }

    private static int integer(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static long longVal(Object v, long def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean bool(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
