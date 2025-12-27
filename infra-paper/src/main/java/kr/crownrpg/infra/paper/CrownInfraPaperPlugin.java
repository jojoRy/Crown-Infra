package kr.crownrpg.infra.paper;

import kr.crownrpg.infra.paper.bootstrap.InfraBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class CrownInfraPaperPlugin extends JavaPlugin {

    private InfraBootstrap bootstrap;

    @Override
    public void onEnable() {
        this.bootstrap = new InfraBootstrap(this);
        try {
            bootstrap.start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to start CrownInfraPaper", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            try {
                bootstrap.stop();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error while stopping CrownInfraPaper", e);
            }
        }
    }
}
