package kr.crownrpg.infra.paper.config;

import org.bukkit.configuration.ConfigurationSection;

public record InfraConfig(String environment, String serverId) {

    public static InfraConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("infra section is missing");
        }
        String environment = trimToEmpty(section.getString("environment"));
        String serverId = trimToEmpty(section.getString("server-id"));
        if (environment.isBlank()) {
            throw new IllegalArgumentException("environment must not be blank");
        }
        if (serverId.isBlank()) {
            throw new IllegalArgumentException("server-id must not be blank");
        }
        return new InfraConfig(environment, serverId);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
