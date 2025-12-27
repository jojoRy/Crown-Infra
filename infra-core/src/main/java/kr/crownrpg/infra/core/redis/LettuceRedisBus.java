package kr.crownrpg.infra.core.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import kr.crownrpg.infra.api.message.InfraMessage;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.api.redis.RedisMessageHandler;
import kr.crownrpg.infra.api.redis.RedisSubscription;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lettuce 기반 Redis Pub/Sub 구현체
 */
public final class LettuceRedisBus implements RedisBus {

    private final RedisClient client;
    private final RedisCodec codec;

    private final StatefulRedisConnection<String, String> publisher;
    private final StatefulRedisPubSubConnection<String, String> subscriber;

    private final Map<String, RedisMessageHandler> handlers = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public LettuceRedisBus(RedisClient client, RedisCodec codec) {
        this.client = Objects.requireNonNull(client, "client");
        this.codec = Objects.requireNonNull(codec, "codec");

        this.publisher = client.connect();
        this.subscriber = client.connectPubSub();

        this.subscriber.addListener(new RedisPubSubAdapter<String, String>() {
            @Override
            public void message(String channel, String message) {
                RedisMessageHandler handler = handlers.get(channel);
                if (handler == null) return;

                try {
                    InfraMessage decoded = codec.decode(message);
                    handler.onMessage(channel, decoded);
                } catch (Exception e) {
                    // infra-core에서는 로깅 책임 없음
                }
            }
        });
    }

    @Override
    public void publish(String channel, InfraMessage message) {
        requireOpen();
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(message, "message");

        String json = codec.encode(message);
        publisher.sync().publish(channel, json);
    }

    @Override
    public RedisSubscription subscribe(String channel, RedisMessageHandler handler) {
        requireOpen();
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(handler, "handler");

        handlers.put(channel, handler);
        subscriber.sync().subscribe(channel);

        return new RedisSubscription() {
            private final AtomicBoolean once = new AtomicBoolean(false);

            @Override
            public String channel() {
                return channel;
            }

            @Override
            public void close() {
                if (!once.compareAndSet(false, true)) return;

                handlers.remove(channel, handler);
                try {
                    subscriber.sync().unsubscribe(channel);
                } catch (Exception ignored) {}
            }
        };
    }

    public void shutdown() {
        if (!closed.compareAndSet(false, true)) return;

        try { subscriber.close(); } catch (Exception ignored) {}
        try { publisher.close(); } catch (Exception ignored) {}
        try { client.shutdown(); } catch (Exception ignored) {}

        handlers.clear();
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("RedisBus is already closed");
        }
    }
}
