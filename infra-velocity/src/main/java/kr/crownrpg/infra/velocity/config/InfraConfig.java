package kr.crownrpg.infra.velocity.config;

import java.util.Map;

public record InfraConfig(String environment, String serverId) {

    public static InfraConfig fromMap(Map<String, Object> section) {
        if (section == null) {
            throw new IllegalArgumentException("infra 섹션이 존재하지 않습니다.");
        }
        String environment = trimToEmpty(section.get("environment"));
        String serverId = trimToEmpty(section.get("server-id"));
        if (environment.isBlank()) {
            throw new IllegalArgumentException("environment 값이 비어 있습니다.");
        }
        if (serverId.isBlank()) {
            throw new IllegalArgumentException("server-id 값이 비어 있습니다.");
        }
        return new InfraConfig(environment, serverId);
    }

    private static String trimToEmpty(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
