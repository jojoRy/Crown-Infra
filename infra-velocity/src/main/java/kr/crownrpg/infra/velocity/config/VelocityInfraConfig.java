package kr.crownrpg.infra.velocity.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class VelocityInfraConfig {

    private final String environment;
    private final String serverId;
    private final Map<String, Object> database;
    private final Map<String, Object> redis;

    private VelocityInfraConfig(String environment, String serverId, Map<String, Object> database, Map<String, Object> redis) {
        this.environment = environment;
        this.serverId = serverId;
        this.database = database;
        this.redis = redis;
    }

    public static VelocityInfraConfig load(Path dataDir, ClassLoader loader) {
        try {
            if (!Files.exists(dataDir)) Files.createDirectories(dataDir);

            Path file = dataDir.resolve("config.yml");
            if (!Files.exists(file)) {
                try (InputStream in = loader.getResourceAsStream("config.yml")) {
                    if (in == null) throw new IllegalStateException("리소스에 기본 config.yml이 존재하지 않습니다.");
                    try (OutputStream out = Files.newOutputStream(file)) {
                        in.transferTo(out);
                    }
                }
            }

            Yaml yaml = new Yaml();
            Map<String, Object> root;
            try (InputStream in = Files.newInputStream(file)) {
                Object obj = yaml.load(in);
                root = (obj instanceof Map<?, ?> m) ? castMap(m) : new HashMap<>();
            }

            String env = str(root.get("environment"), "prod");
            String serverId = str(root.get("server-id"), "proxy-1");

            Map<String, Object> db = section(root.get("database"));
            Map<String, Object> redis = section(root.get("redis"));

            return new VelocityInfraConfig(env, serverId, db, redis);

        } catch (Exception e) {
            throw new RuntimeException("config.yml을 불러오지 못했습니다.", e);
        }
    }

    public String environment() { return environment; }
    public String serverId() { return serverId; }
    public Map<String, Object> database() { return database; }
    public Map<String, Object> redis() { return redis; }

    private static Map<String, Object> section(Object v) {
        if (v instanceof Map<?, ?> m) return castMap(m);
        return new HashMap<>();
    }

    private static Map<String, Object> castMap(Map<?, ?> m) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() == null) continue;
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        return String.valueOf(v);
    }
}
