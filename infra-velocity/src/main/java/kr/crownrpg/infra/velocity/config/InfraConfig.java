package kr.crownrpg.infra.velocity.config;

import java.util.Map;

public record InfraConfig(String environment, String serverId) {

    public static InfraConfig fromMap(Map<String, Object> section) {
        if (section == null) {
            throw new IllegalArgumentException("infra section is missing");
        }
        String environment = trimToEmpty(section.get("environment"));
        String serverId = trimToEmpty(section.get("server-id"));
        if (environment.isBlank()) {
            throw new IllegalArgumentException("environment must not be blank");
        }
        if (serverId.isBlank()) {
            throw new IllegalArgumentException("server-id must not be blank");
        }
        return new InfraConfig(environment, serverId);
    }

    private static String trimToEmpty(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
