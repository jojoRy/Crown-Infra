package kr.crownrpg.infra.api.database;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable database configuration container.
 */
public final class DatabaseConfig {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;
    private final Map<String, String> properties;

    public DatabaseConfig(String host, int port, String database, String username, String password, int poolSize, Map<String, String> properties) {
        this.host = nullSafe(host);
        this.port = port;
        this.database = nullSafe(database);
        this.username = nullSafe(username);
        this.password = password;
        this.poolSize = poolSize;
        this.properties = toUnmodifiableMap(properties);
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String database() {
        return database;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public int poolSize() {
        return poolSize;
    }

    public Map<String, String> properties() {
        return properties;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, String> toUnmodifiableMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(Map.copyOf(source));
    }

    @Override
    public String toString() {
        return "DatabaseConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", database='" + database + '\'' +
                ", username='" + username + '\'' +
                ", password='" + (password == null || password.isEmpty() ? "" : "****") + '\'' +
                ", poolSize=" + poolSize +
                ", properties=" + properties +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DatabaseConfig that)) return false;
        return port == that.port && poolSize == that.poolSize && Objects.equals(host, that.host) && Objects.equals(database, that.database) && Objects.equals(username, that.username) && Objects.equals(password, that.password) && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, database, username, password, poolSize, properties);
    }
}
