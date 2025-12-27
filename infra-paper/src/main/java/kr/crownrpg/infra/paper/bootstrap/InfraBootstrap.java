package kr.crownrpg.infra.paper.bootstrap;

import kr.crownrpg.lib.service.ServiceRegistry;
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

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class InfraBootstrap {

    private final JavaPlugin plugin;
    private final Logger logger;

    private InfraContext context;
    private RedisBinder redisBinder;
    private DatabaseBinder databaseBinder;
    private PaperPubSubBootstrap pubSubBootstrap;
    private boolean started;
    private boolean requireServiceRegistry;

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
            this.requireServiceRegistry = infraConfig.requireServiceRegistry();

            this.redisBinder = new RedisBinder(logger, redisConfig, context);

            this.databaseBinder = new DatabaseBinder(logger, databaseConfig);

            redisBinder.start();
            databaseBinder.start();

            this.pubSubBootstrap = new PaperPubSubBootstrap(logger, redisBinder.getBus(), context);
            pubSubBootstrap.start();

            registerService(InfraContext.class, context);
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

    private <T> void registerService(Class<T> serviceType, T instance) {
        try {
            ServiceRegistry.register(serviceType, instance);
            logger.info("Registered " + serviceType.getSimpleName() + " into CrownLib ServiceRegistry");
        } catch (NoClassDefFoundError e) {
            logger.warning("CrownLib not found; skipped ServiceRegistry registration for " + serviceType.getSimpleName());
        } catch (Throwable t) {
            Level level = requireServiceRegistry ? Level.SEVERE : Level.WARNING;
            logger.log(level, "Failed to register " + serviceType.getSimpleName() + " with CrownLib ServiceRegistry; infra will continue running", t);
        }
    }
}
