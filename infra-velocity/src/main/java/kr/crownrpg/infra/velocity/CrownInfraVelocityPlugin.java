package kr.crownrpg.infra.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import kr.crownrpg.infra.api.database.DatabaseClient;
import kr.crownrpg.infra.velocity.binder.MySqlBinder;
import kr.crownrpg.infra.velocity.binder.RedisBinder;
import kr.crownrpg.infra.velocity.config.VelocityInfraConfig;
import kr.crownrpg.infra.velocity.lifecycle.CloseableRegistry;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

@Plugin(
        id = "crowninfra",
        name = "CrownInfra",
        version = "1.0.0",
        description = "CrownRPG Infra binder (Velocity) - MySQL(Hikari) + Redis(Lettuce)",
        authors = {"jjoRy"}
)
public final class CrownInfraVelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private final CloseableRegistry closeables = new CloseableRegistry();

    private VelocityInfraConfig cfg;
    private MySqlBinder mysql;
    private RedisBinder redis;

    @Inject
    public CrownInfraVelocityPlugin(
            ProxyServer proxy,
            Logger logger,
            @DataDirectory Path dataDirectory
    ) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        try {
            this.cfg = VelocityInfraConfig.load(dataDirectory, getClass().getClassLoader());

            logger.info("[CrownInfra] Boot start (Velocity)"
                    + " (env=" + cfg.environment()
                    + ", serverId=" + cfg.serverId() + ")");

            // MySQL
            this.mysql = new MySqlBinder();
            mysql.start(cfg.database());
            closeables.register(mysql);

            // Redis
            this.redis = new RedisBinder();
            redis.start(cfg.redis());
            closeables.register(redis);

            warmup(mysql.client());

            logger.info("[CrownInfra] Boot OK (Velocity) - MySQL + Redis ready");

        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[CrownInfra] Boot FAILED (Velocity).", t);
            // Velocity는 Paper처럼 disablePlugin이 없어서 "실패해도 살아있게" 두되,
            // 핵심 서비스 접근 시 예외가 나도록 두는 것이 일반적.
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent e) {
        closeables.closeAllQuietly(logger);
        logger.info("[CrownInfra] Stopped (Velocity).");
    }

    public DatabaseClient database() {
        if (mysql == null) throw new IllegalStateException("CrownInfra not initialized");
        return mysql.client();
    }

    public RedisBinder redis() {
        if (redis == null) throw new IllegalStateException("CrownInfra not initialized");
        return redis;
    }

    private void warmup(DatabaseClient db) {
        try {
            db.query(exec -> {
                exec.executeQuery("SELECT 1");
                return null;
            }).exceptionally(ex -> {
                logger.warning("[CrownInfra] Warmup query failed: " + ex.getMessage());
                return null;
            });
        } catch (Throwable ignore) {}
    }
}
