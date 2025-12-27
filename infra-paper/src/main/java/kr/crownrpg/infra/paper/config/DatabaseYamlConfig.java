package kr.crownrpg.infra.paper.config;

import kr.crownrpg.infra.api.database.DatabaseConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DatabaseYamlConfig(String host, int port, String database, String username, String password, int poolSize, Map<String, String> properties) {

    public static DatabaseYamlConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("database section is missing");
        }
        String host = trimToEmpty(section.getString("host"));
        int port = section.getInt("port", 3306);
        String database = trimToEmpty(section.getString("database"));
        String username = trimToEmpty(section.getString("username"));
        String password = section.getString("password", "");
        int poolSize = section.getInt("pool-size", 10);
        Map<String, String> properties = readProperties(section.getConfigurationSection("properties"));
        if (host.isBlank()) {
            throw new IllegalArgumentException("database.host must not be blank");
        }
        if (database.isBlank()) {
            throw new IllegalArgumentException("database.database must not be blank");
        }
        if (username.isBlank()) {
            throw new IllegalArgumentException("database.username must not be blank");
        }
        return new DatabaseYamlConfig(host, port, database, username, password, poolSize, properties);
    }

    public DatabaseConfig toDatabaseConfig() {
        return new DatabaseConfig(host, port, database, username, password, poolSize, properties);
    }

    private static Map<String, String> readProperties(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key, "");
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
