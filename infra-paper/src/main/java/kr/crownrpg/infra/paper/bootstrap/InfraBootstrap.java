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

/**
 * Paper 플러그인에서 인프라 구성 요소를 초기화·종료하는 부트스트랩 진입점.
 * 설정 로드 → Redis/DB 바인더 초기화 → PubSub 부팅 → 서비스 레지스트리 등록 순으로 동작한다.
 */
public final class InfraBootstrap {

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

    /**
     * 플러그인 설정을 읽고 Redis/DB/메시지 버스를 순서대로 시작한다.
     * 실패 시 각 구성 요소를 안전하게 내려가며 플러그인을 비활성화한다.
     */
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

            this.redisBinder = new RedisBinder(logger, redisConfig, context);

            this.databaseBinder = new DatabaseBinder(logger, databaseConfig);

            redisBinder.start();
            databaseBinder.start();

            this.pubSubBootstrap = new PaperPubSubBootstrap(logger, redisBinder.getBus(), context);
            pubSubBootstrap.start();

            registerRequiredService(InfraContext.class, context);
            registerRequiredService(RedisBus.class, redisBinder.getBus());
            registerRequiredService(DatabaseService.class, databaseBinder.getService());

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

    /**
     * 등록된 구성 요소를 역순으로 종료하여 리소스 누수를 방지한다.
     */
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

    /**
     * CrownLib의 ServiceRegistry에 필수 서비스들을 등록한다.
     * 등록 실패 시 부팅을 중단하고 예외를 전파한다.
     */
    private <T> void registerRequiredService(Class<T> serviceType, T instance) {
        try {
            ServiceRegistry.register(serviceType, instance);
            logger.info("Registered " + serviceType.getSimpleName() + " into CrownLib ServiceRegistry");
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to register " + serviceType.getSimpleName() + " with CrownLib ServiceRegistry", t);
            throw new IllegalStateException("CrownInfraPaper startup failed: required service registration unsuccessful", t);
        }
    }
}
