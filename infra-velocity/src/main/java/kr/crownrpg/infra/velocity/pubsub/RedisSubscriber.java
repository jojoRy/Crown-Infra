package kr.crownrpg.infra.velocity.pubsub;

import kr.crownrpg.infra.api.context.InfraContext;
import kr.crownrpg.infra.api.message.InfraMessage;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.api.redis.RedisMessageRules;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/**
 * Sets up Redis PubSub subscriptions, applies the mandatory guards, and delegates to the dispatcher.
 */
public final class RedisSubscriber {

    private static final String LOG_PREFIX = "[CrownInfra-Velocity] ";

    private final Logger logger;
    private final RedisBus bus;
    private final InfraContext context;
    private final MessageDispatcher dispatcher;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public RedisSubscriber(Logger logger, RedisBus bus, InfraContext context, MessageDispatcher dispatcher) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.context = Objects.requireNonNull(context, "context");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public synchronized void start(Collection<String> channels) {
        if (started.get()) {
            return;
        }
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("구독할 채널 목록이 비어 있습니다.");
        }
        for (String channel : channels) {
            bus.subscribe(channel, (ch, message) -> handle(message));
        }
        started.set(true);
        logger.info(LOG_PREFIX + "Redis 구독을 시작했습니다: {}", channels);
    }

    public synchronized void stop() {
        started.set(false);
        logger.info(LOG_PREFIX + "Redis 구독을 종료했습니다.");
    }

    private void handle(InfraMessage message) {
        if (!started.get()) {
            return;
        }
        if (!RedisMessageRules.shouldProcess(message, context.environment(), context.serverId())) {
            return;
        }
        dispatcher.dispatch(message);
    }
}

