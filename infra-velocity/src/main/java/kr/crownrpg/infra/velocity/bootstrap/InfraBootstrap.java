package kr.crownrpg.infra.velocity.bootstrap;

import kr.crownrpg.infra.api.context.InfraContext;
import kr.crownrpg.infra.api.database.DatabaseService;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.velocity.binder.DatabaseBinder;
import kr.crownrpg.infra.velocity.binder.RedisBinder;
import kr.crownrpg.infra.velocity.config.DatabaseYamlConfig;
import kr.crownrpg.infra.velocity.config.InfraConfig;
import kr.crownrpg.infra.velocity.config.RedisYamlConfig;
import kr.crownrpg.infra.velocity.pubsub.VelocityPubSubBootstrap;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class InfraBootstrap {

    private final Logger logger;
    private final Path dataDirectory;

    private InfraContext context;
    private RedisBinder redisBinder;
    private DatabaseBinder databaseBinder;
    private VelocityPubSubBootstrap pubSubBootstrap;
    private boolean started;

    public InfraBootstrap(Logger logger, Path dataDirectory) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            Map<String, Object> root = loadConfig();
            InfraConfig infraConfig = InfraConfig.fromMap(castSection(root.get("infra")));
            RedisYamlConfig redisConfig = RedisYamlConfig.fromMap(castSection(root.get("redis")));
            DatabaseYamlConfig databaseConfig = DatabaseYamlConfig.fromMap(castSection(root.get("database")));

            this.context = new InfraContext(infraConfig.environment(), infraConfig.serverId());
            this.redisBinder = new RedisBinder(logger, redisConfig, context);
            this.databaseBinder = new DatabaseBinder(logger, databaseConfig);

            redisBinder.start();
            databaseBinder.start();

            this.pubSubBootstrap = new VelocityPubSubBootstrap(logger, redisBinder.getBus(), context);
            pubSubBootstrap.start();

            logger.info("CrownInfraVelocity bootstrap completed for " + context);
            started = true;
        } catch (Exception e) {
            logger.error("Failed to start CrownInfraVelocity", e);
            safeStop(pubSubBootstrap);
            safeStop(databaseBinder);
            safeStop(redisBinder);
            throw e;
        }
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }
        safeStop(databaseBinder);
        safeStop(pubSubBootstrap);
        safeStop(redisBinder);
        started = false;
    }

    private void safeStop(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            logger.error("Error while stopping binder", e);
        }
    }

    private Map<String, Object> loadConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path configPath = dataDirectory.resolve("config.yml");
            if (!Files.exists(configPath)) {
                copyDefaultConfig(configPath);
            }
            try (InputStream in = Files.newInputStream(configPath)) {
                Yaml yaml = new Yaml();
                Object loaded = yaml.load(in);
                if (loaded instanceof Map) {
                    return new LinkedHashMap<>((Map<String, Object>) loaded);
                }
                return new LinkedHashMap<>();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config.yml", e);
        }
    }

    private void copyDefaultConfig(Path target) throws IOException {
        try (InputStream in = InfraBootstrap.class.getClassLoader().getResourceAsStream("config.yml")) {
            if (in == null) {
                throw new IllegalStateException("Default config.yml is missing from resources");
            }
            Files.copy(in, target);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castSection(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        }
        throw new IllegalArgumentException("Invalid configuration section: " + value);
    }

}
