package kr.crownrpg.infra.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import kr.crownrpg.infra.velocity.bootstrap.InfraBootstrap;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "crowninfra", name = "CrownInfra", version = "1.0.0-SNAPSHOT", authors = {"CrownRPG"})
public final class CrownInfraVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private InfraBootstrap bootstrap;

    @Inject
    public CrownInfraVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.bootstrap = new InfraBootstrap(logger, dataDirectory);
        try {
            bootstrap.start();
        } catch (Exception e) {
            logger.error("Failed to start CrownInfraVelocity", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (bootstrap != null) {
            try {
                bootstrap.stop();
            } catch (Exception e) {
                logger.error("Error while stopping CrownInfraVelocity", e);
            }
        }
    }
}
