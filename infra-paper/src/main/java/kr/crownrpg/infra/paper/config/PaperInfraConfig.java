package kr.crownrpg.infra.paper.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PaperInfraConfig {

    private final String environment;
    private final String serverId;
    private final Map<String, Object> database;
    private final Map<String, Object> redis;

    private PaperInfraConfig(String environment, String serverId, Map<String, Object> database, Map<String, Object> redis) {
        this.environment = environment;
        this.serverId = serverId;
        this.database = database;
        this.redis = redis;
    }

    public static PaperInfraConfig load(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        String env = plugin.getConfig().getString("environment", "prod");
        String serverId = plugin.getConfig().getString("server-id", "paper-1");

        Map<String, Object> db = readSection(plugin.getConfig().getConfigurationSection("database"));
        Map<String, Object> redis = readSection(plugin.getConfig().getConfigurationSection("redis"));

        return new PaperInfraConfig(env, serverId, db, redis);
    }

    public String environment() {
        return environment;
    }

    public String serverId() {
        return serverId;
    }

    public Map<String, Object> database() {
        return database;
    }

    public Map<String, Object> redis() {
        return redis;
    }

    private static Map<String, Object> readSection(ConfigurationSection sec) {
        Map<String, Object> map = new HashMap<>();
        if (sec == null) return map;

        for (String key : sec.getKeys(false)) {
            map.put(key, sec.get(key));
        }
        return map;
    }
}
