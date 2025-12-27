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

    private static final String LOG_PREFIX = "[CrownInfra-Velocity] ";

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

            logger.info("{}부트스트랩이 완료되었습니다: {}", LOG_PREFIX, context);
            started = true;
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "부트스트랩 중 예외가 발생했습니다.", e);
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
            logger.error(LOG_PREFIX + "종료 처리 중 오류가 발생했습니다.", e);
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
            throw new IllegalStateException("config.yml을 불러오지 못했습니다.", e);
        }
    }

    private void copyDefaultConfig(Path target) throws IOException {
        try (InputStream in = InfraBootstrap.class.getClassLoader().getResourceAsStream("config.yml")) {
            if (in == null) {
                throw new IllegalStateException("기본 config.yml 리소스가 존재하지 않습니다.");
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
        throw new IllegalArgumentException("잘못된 설정 섹션입니다: " + value);
    }

}
