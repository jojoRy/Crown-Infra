package kr.crownrpg.infra.paper.config;

import org.bukkit.configuration.ConfigurationSection;

public record RedisYamlConfig(String host, int port, boolean ssl, String password, long timeoutMs) {

    public static RedisYamlConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("redis section is missing");
        }
        String host = trimToEmpty(section.getString("host"));
        int port = section.getInt("port", 6379);
        boolean ssl = section.getBoolean("ssl", false);
        String password = section.getString("password", "");
        long timeout = section.getLong("timeout-ms", 5000L);
        if (host.isBlank()) {
            throw new IllegalArgumentException("redis.host must not be blank");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("redis.port must be positive");
        }
        if (timeout <= 0) {
            throw new IllegalArgumentException("redis.timeout-ms must be positive");
        }
        return new RedisYamlConfig(host, port, ssl, password, timeout);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
