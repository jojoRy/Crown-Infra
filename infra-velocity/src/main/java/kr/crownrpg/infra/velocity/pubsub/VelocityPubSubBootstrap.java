package kr.crownrpg.infra.velocity.pubsub;

import kr.crownrpg.infra.api.context.InfraContext;
import kr.crownrpg.infra.api.message.InfraMessage;
import kr.crownrpg.infra.api.message.MessageTypes;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.api.redis.RedisChannels;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/**
 * Wires Redis subscriptions and sample message handlers for Velocity proxies.
 */
public final class VelocityPubSubBootstrap implements AutoCloseable {

    private static final String TYPE_HEARTBEAT = MessageTypes.compose("server", "heartbeat");
    private static final String TYPE_NOTICE = MessageTypes.compose("broadcast", "notice");

    private static final String LOG_PREFIX = "[CrownInfra-Velocity] ";

    private final Logger logger;
    private final RedisBus bus;
    private final InfraContext context;
    private final MessageDispatcher dispatcher;
    private final RedisSubscriber subscriber;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public VelocityPubSubBootstrap(Logger logger, RedisBus bus, InfraContext context) {
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
                RedisChannels.forProxy(context.environment()),
                RedisChannels.forBroadcast(context.environment())
        );
        subscriber.start(channels);
        started.set(true);
        logger.info(LOG_PREFIX + "Redis Pub/Sub 구독을 시작했습니다: {}", channels);
    }

    public synchronized void stop() {
        if (!started.get()) {
            return;
        }
        subscriber.stop();
        started.set(false);
        logger.info(LOG_PREFIX + "Redis Pub/Sub 구독을 종료했습니다.");
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
        bus.publish(RedisChannels.forPaper(context.environment()), message);
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
                logger.info(LOG_PREFIX + "하트비트를 수신했습니다: 서버={} 메타={}", message.fromServerId(), message.meta().messageId()));

        dispatcher.register(TYPE_NOTICE, message ->
                logger.info(LOG_PREFIX + "공지 메시지를 수신했습니다: {}", message.payload()));
    }

    private void ensureRunning() {
        if (!started.get()) {
            throw new IllegalStateException("Pub/Sub이 시작되지 않았습니다.");
        }
    }
}

