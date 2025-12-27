package kr.crownrpg.infra.paper;

import kr.crownrpg.infra.api.database.DatabaseClient;
import kr.crownrpg.infra.paper.binder.MySqlBinder;
import kr.crownrpg.infra.paper.binder.RedisBinder;
import kr.crownrpg.infra.paper.config.PaperInfraConfig;
import kr.crownrpg.infra.paper.lifecycle.CloseableRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.logging.Level;

public final class CrownInfraPaperPlugin extends JavaPlugin {

    private final CloseableRegistry closeables = new CloseableRegistry();

    private PaperInfraConfig cfg;

    private MySqlBinder mysql;
    private RedisBinder redis;

    @Override
    public void onEnable() {
        try {
            // 1) load config
            this.cfg = PaperInfraConfig.load(this);

            getLogger().info("[CrownInfra] Boot start"
                    + " (env=" + cfg.environment()
                    + ", serverId=" + cfg.serverId() + ")");

            // 2) MySQL start
            this.mysql = new MySqlBinder();
            mysql.start(cfg.database());
            closeables.register(mysql);

            // 3) Redis start
            this.redis = new RedisBinder();
            redis.start(cfg.redis());
            closeables.register(redis);

            // 4) Optional: basic sanity query (non-fatal)
            warmup(mysql.client());

            getLogger().info("[CrownInfra] Boot OK (MySQL + Redis ready)");

        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "[CrownInfra] Boot FAILED. Disabling plugin.", t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        closeables.closeAllQuietly(getLogger());
        getLogger().info("[CrownInfra] Stopped.");
    }

    public DatabaseClient database() {
        if (mysql == null) throw new IllegalStateException("CrownInfra not enabled");
        return mysql.client();
    }

    public RedisBinder redis() {
        if (redis == null) throw new IllegalStateException("CrownInfra not enabled");
        return redis;
    }

    private void warmup(DatabaseClient db) {
        try {
            db.query(exec -> {
                exec.executeQuery("SELECT 1");
                return null;
            }).exceptionally(ex -> {
                getLogger().warning("[CrownInfra] Warmup query failed: " + ex.getMessage());
                return null;
            });
        } catch (Throwable ignore) {
            // warmup은 실패해도 치명적이지 않게
        }
    }
}
