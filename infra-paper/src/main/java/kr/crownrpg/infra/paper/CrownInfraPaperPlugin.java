package kr.crownrpg.infra.paper;

import kr.crownrpg.infra.paper.bootstrap.InfraBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Paper 서버에서 구동되는 CrownInfra 메인 플러그인.
 * Bukkit 수명주기 훅을 통해 {@link InfraBootstrap}을 시작/종료한다.
 */
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
