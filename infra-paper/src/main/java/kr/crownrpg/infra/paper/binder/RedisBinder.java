package kr.crownrpg.infra.paper.binder;

import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.core.redis.LettuceRedisBus;
import kr.crownrpg.infra.core.redis.RedisClientFactory;
import kr.crownrpg.infra.paper.config.RedisYamlConfig;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RedisBinder {

    private final Logger logger;
    private final RedisYamlConfig config;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private LettuceRedisBus bus;

    public RedisBinder(Logger logger, RedisYamlConfig config) {
        this.logger = logger;
        this.config = config;
    }

    public synchronized void start() {
        if (started.get()) {
            return;
        }
        try {
            RedisClientFactory factory = new RedisClientFactory(
                    config.host(),
                    config.port(),
                    config.ssl(),
                    config.password(),
                    Duration.ofMillis(config.timeoutMs()),
                    0
            );
            this.bus = new LettuceRedisBus(factory);
            bus.start();
            started.set(true);
            logger.info("RedisBinder started");
        } catch (Exception e) {
            started.set(false);
            throw e;
        }
    }

    public synchronized void stop() {
        if (!started.get()) {
            return;
        }
        try {
            if (bus != null) {
                bus.stop();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to stop RedisBinder", e);
        } finally {
            started.set(false);
        }
    }

    public RedisBus getBus() {
        return bus;
    }

    public boolean isStarted() {
        return started.get();
    }
}
