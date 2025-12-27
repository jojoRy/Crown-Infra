package kr.crownrpg.infra.paper.pubsub;

import kr.crownrpg.infra.api.context.InfraContext;
import kr.crownrpg.infra.api.message.InfraMessage;
import kr.crownrpg.infra.api.message.MessageTypes;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.api.redis.RedisChannels;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Wires Redis subscriptions and sample message handlers for Paper servers.
 */
public final class PaperPubSubBootstrap implements AutoCloseable {

    private static final String TYPE_HEARTBEAT = MessageTypes.compose("server", "heartbeat");
    private static final String TYPE_NOTICE = MessageTypes.compose("broadcast", "notice");

    private final Logger logger;
    private final RedisBus bus;
    private final InfraContext context;
    private final MessageDispatcher dispatcher;
    private final RedisSubscriber subscriber;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public PaperPubSubBootstrap(Logger logger, RedisBus bus, InfraContext context) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.context = Objects.requireNonNull(context, "context");
        this.dispatcher = new MessageDispatcher(logger);
        this.subscriber = new RedisSubscriber(logger, bus, context, dispatcher);
    }

    public synchronized void start() {
        if (started.get()) {
            return;
        }
        registerHandlers();
        List<String> channels = List.of(
                RedisChannels.forPaper(context.environment()),
                RedisChannels.forBroadcast(context.environment())
        );
        subscriber.start(channels);
        started.set(true);
    }

    public synchronized void stop() {
        if (!started.get()) {
            return;
        }
        subscriber.stop();
        started.set(false);
    }

    @Override
    public void close() {
        stop();
    }

    public void publishHeartbeat() {
        ensureRunning();
        InfraMessage message = InfraMessage.text(
                context.environment(),
                context.serverId(),
                TYPE_HEARTBEAT,
                ""
        );
        bus.publish(RedisChannels.forProxy(context.environment()), message);
    }

    public void publishNotice(String notice) {
        ensureRunning();
        InfraMessage message = InfraMessage.text(
                context.environment(),
                context.serverId(),
                TYPE_NOTICE,
                notice == null ? "" : notice
        );
        bus.publish(RedisChannels.forBroadcast(context.environment()), message);
    }

    private void registerHandlers() {
        dispatcher.register(TYPE_HEARTBEAT, message ->
                logger.info("Heartbeat received from " + message.fromServerId() + " meta=" + message.meta().messageId()));

        dispatcher.register(TYPE_NOTICE, message ->
                logger.info("Notice received: " + message.payload()));
    }

    private void ensureRunning() {
        if (!started.get()) {
            throw new IllegalStateException("PubSub is not started");
        }
    }
}

