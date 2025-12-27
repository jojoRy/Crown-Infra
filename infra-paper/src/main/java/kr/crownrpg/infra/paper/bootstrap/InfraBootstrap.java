package kr.crownrpg.infra.paper.bootstrap;

import kr.crownrpg.infra.api.context.InfraContext;
import kr.crownrpg.infra.api.database.DatabaseService;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.paper.binder.DatabaseBinder;
import kr.crownrpg.infra.paper.binder.RedisBinder;
import kr.crownrpg.infra.paper.config.DatabaseYamlConfig;
import kr.crownrpg.infra.paper.config.InfraConfig;
import kr.crownrpg.infra.paper.config.RedisYamlConfig;
import kr.crownrpg.infra.paper.pubsub.PaperPubSubBootstrap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class InfraBootstrap {

    private static final List<String> REGISTRY_CLASS_CANDIDATES = List.of(
            "kr.crownrpg.crownlib.ServiceRegistry",
            "kr.crownrpg.crownlib.registry.ServiceRegistry"
    );

    private final JavaPlugin plugin;
    private final Logger logger;

    private InfraContext context;
    private RedisBinder redisBinder;
    private DatabaseBinder databaseBinder;
    private PaperPubSubBootstrap pubSubBootstrap;
    private boolean started;

    public InfraBootstrap(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            plugin.saveDefaultConfig();
            FileConfiguration config = plugin.getConfig();

            InfraConfig infraConfig = InfraConfig.fromConfig(config.getConfigurationSection("infra"));
            RedisYamlConfig redisConfig = RedisYamlConfig.fromConfig(config.getConfigurationSection("redis"));
            DatabaseYamlConfig databaseConfig = DatabaseYamlConfig.fromConfig(config.getConfigurationSection("database"));

            this.context = new InfraContext(infraConfig.environment(), infraConfig.serverId());

            this.redisBinder = new RedisBinder(logger, redisConfig);

            this.databaseBinder = new DatabaseBinder(logger, databaseConfig);

            redisBinder.start();
            databaseBinder.start();

            this.pubSubBootstrap = new PaperPubSubBootstrap(logger, redisBinder.getBus(), context);
            pubSubBootstrap.start();

            registerService(RedisBus.class, redisBinder.getBus());
            registerService(DatabaseService.class, databaseBinder.getService());

            logger.info("CrownInfra bootstrap completed for " + context);
            started = true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start CrownInfraPaper", e);
            try {
                if (pubSubBootstrap != null) {
                    pubSubBootstrap.stop();
                }
            } catch (Exception stopError) {
                logger.log(Level.SEVERE, "Failed to stop pubsub after startup error", stopError);
            }
            try {
                if (databaseBinder != null) {
                    databaseBinder.stop();
                }
            } catch (Exception stopError) {
                logger.log(Level.SEVERE, "Failed to stop database binder after startup error", stopError);
            }
            try {
                if (redisBinder != null) {
                    redisBinder.stop();
                }
            } catch (Exception stopError) {
                logger.log(Level.SEVERE, "Failed to stop redis binder after startup error", stopError);
            }
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            throw e;
        }
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }
        try {
            if (databaseBinder != null) {
                databaseBinder.stop();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error while stopping database binder", e);
        }
        try {
            if (pubSubBootstrap != null) {
                pubSubBootstrap.stop();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error while stopping pubsub", e);
        }
        try {
            if (redisBinder != null) {
                redisBinder.stop();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error while stopping redis binder", e);
        }
        started = false;
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
