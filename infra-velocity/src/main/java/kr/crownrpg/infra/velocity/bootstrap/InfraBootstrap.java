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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InfraBootstrap {

    private static final List<String> REGISTRY_CLASS_CANDIDATES = List.of(
            "kr.crownrpg.crownlib.ServiceRegistry",
            "kr.crownrpg.crownlib.registry.ServiceRegistry"
    );

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
            this.redisBinder = new RedisBinder(logger, redisConfig);
            this.databaseBinder = new DatabaseBinder(logger, databaseConfig);

            redisBinder.start();
            databaseBinder.start();

            this.pubSubBootstrap = new VelocityPubSubBootstrap(logger, redisBinder.getBus(), context);
            pubSubBootstrap.start();

            registerService(RedisBus.class, redisBinder.getBus());
            registerService(DatabaseService.class, databaseBinder.getService());

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

    private void registerService(Class<?> serviceType, Object instance) {
        IllegalStateException failure = new IllegalStateException("CrownLib ServiceRegistry is not available");
        for (String className : REGISTRY_CLASS_CANDIDATES) {
            try {
                Class<?> registryClass = Class.forName(className);
                Object registryInstance = resolveRegistryInstance(registryClass);
                Method registerMethod = findRegistrationMethod(registryClass, "registerOnce");
                if (registerMethod == null) {
                    registerMethod = findRegistrationMethod(registryClass, "register");
                }
                if (registerMethod == null) {
                    continue;
                }
                boolean staticMethod = Modifier.isStatic(registerMethod.getModifiers());
                Object target = staticMethod ? null : registryInstance;
                if (target == null && !staticMethod) {
                    continue;
                }
                registerMethod.invoke(target, serviceType, instance);
                return;
            } catch (Exception e) {
                failure = new IllegalStateException("Failed to register service with CrownLib", e);
            }
        }
        throw failure;
    }

    private Method findRegistrationMethod(Class<?> registryClass, String name) {
        for (Method method : registryClass.getMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            if (method.getParameterCount() == 2 && Class.class.isAssignableFrom(method.getParameterTypes()[0])) {
                return method;
            }
        }
        return null;
    }

    private Object resolveRegistryInstance(Class<?> registryClass) {
        for (String methodName : List.of("getInstance", "instance", "global", "get")) {
            try {
                Method method = registryClass.getMethod(methodName);
                if (Modifier.isStatic(method.getModifiers()) && registryClass.isAssignableFrom(method.getReturnType())) {
                    return method.invoke(null);
                }
            } catch (Exception ignored) {
                // continue search
            }
        }
        try {
            Field field = registryClass.getField("INSTANCE");
            if (Modifier.isStatic(field.getModifiers()) && registryClass.isAssignableFrom(field.getType())) {
                return field.get(null);
            }
        } catch (Exception ignored) {
            // continue search
        }
        try {
            Constructor<?> constructor = registryClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception ignored) {
            return null;
        }
    }
}
