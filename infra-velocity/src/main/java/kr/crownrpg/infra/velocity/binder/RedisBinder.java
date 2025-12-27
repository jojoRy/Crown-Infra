package kr.crownrpg.infra.velocity.binder;

import kr.crownrpg.infra.api.context.InfraContext;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.core.redis.LettuceRedisBus;
import kr.crownrpg.infra.core.redis.RedisClientFactory;
import kr.crownrpg.infra.velocity.config.RedisYamlConfig;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RedisBinder implements AutoCloseable {

    private final Logger logger;
    private final RedisYamlConfig config;
    private final InfraContext context;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private LettuceRedisBus bus;

    public RedisBinder(Logger logger, RedisYamlConfig config, InfraContext context) {
        this.logger = logger;
        this.config = config;
        this.context = context;
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
            this.bus = new LettuceRedisBus(factory, context);
            bus.start();
            started.set(true);
            logger.info("RedisBinder가 시작되었습니다");
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
            logger.error("RedisBinder 종료에 실패했습니다", e);
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

    @Override
    public void close() {
        stop();
    }
}
