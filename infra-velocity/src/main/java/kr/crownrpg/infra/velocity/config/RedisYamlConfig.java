package kr.crownrpg.infra.velocity.config;

import java.util.Map;

public record RedisYamlConfig(String host, int port, boolean ssl, String password, long timeoutMs) {

    public static RedisYamlConfig fromMap(Map<String, Object> section) {
        if (section == null) {
            throw new IllegalArgumentException("redis section is missing");
        }
        String host = trimToEmpty(section.get("host"));
        int port = toInt(section.get("port"), 6379);
        boolean ssl = toBoolean(section.get("ssl"), false);
        String password = section.get("password") == null ? "" : section.get("password").toString();
        long timeout = toLong(section.get("timeout-ms"), 5000L);
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

    private static long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
