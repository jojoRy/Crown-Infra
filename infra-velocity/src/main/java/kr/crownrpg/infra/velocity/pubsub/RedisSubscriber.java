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
            throw new IllegalArgumentException("channels must not be empty");
        }
        for (String channel : channels) {
            bus.subscribe(channel, (ch, message) -> handle(message));
        }
        started.set(true);
        logger.info("RedisSubscriber started for channels: {}", channels);
    }

    public synchronized void stop() {
        started.set(false);
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

