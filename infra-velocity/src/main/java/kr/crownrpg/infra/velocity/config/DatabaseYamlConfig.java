package kr.crownrpg.infra.velocity.config;

import kr.crownrpg.infra.api.database.DatabaseConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DatabaseYamlConfig(String host, int port, String database, String username, String password, int poolSize, Map<String, String> properties) {

    public static DatabaseYamlConfig fromMap(Map<String, Object> section) {
        if (section == null) {
            throw new IllegalArgumentException("database 섹션이 존재하지 않습니다.");
        }
        String host = trimToEmpty(section.get("host"));
        int port = toInt(section.get("port"), 3306);
        String database = trimToEmpty(section.get("database"));
        String username = trimToEmpty(section.get("username"));
        String password = section.get("password") == null ? "" : section.get("password").toString();
        int poolSize = toInt(section.get("pool-size"), 10);
        Map<String, String> properties = readProperties(section.get("properties"));
        if (host.isBlank()) {
            throw new IllegalArgumentException("database.host 값이 비어 있습니다.");
        }
        if (database.isBlank()) {
            throw new IllegalArgumentException("database.database 값이 비어 있습니다.");
        }
        if (username.isBlank()) {
            throw new IllegalArgumentException("database.username 값이 비어 있습니다.");
        }
        return new DatabaseYamlConfig(host, port, database, username, password, poolSize, properties);
    }

    public DatabaseConfig toDatabaseConfig() {
        return new DatabaseConfig(host, port, database, username, password, poolSize, properties);
    }

    private static Map<String, String> readProperties(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (!(value instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().toString();
            String v = entry.getValue() == null ? "" : entry.getValue().toString();
            result.put(key, v);
        }
        return Collections.unmodifiableMap(result);
    }

    private static String trimToEmpty(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
