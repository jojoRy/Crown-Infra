package kr.crownrpg.infra.core.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.StatefulRedisPubSubConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import kr.crownrpg.infra.api.message.InfraMessage;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.api.redis.RedisMessageHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RedisBus implementation backed by Lettuce Pub/Sub.
 */
public class LettuceRedisBus implements RedisBus {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RedisClientFactory clientFactory;
    private final Map<String, List<RedisMessageHandler>> handlers = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private RedisClient client;
    private StatefulRedisConnection<String, String> publishConnection;
    private StatefulRedisPubSubConnection<String, String> subscribeConnection;

    public LettuceRedisBus(RedisClientFactory clientFactory) {
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
    }

    @Override
    public void start() {
        if (stopped.get()) {
            throw new IllegalStateException("RedisBus has been stopped and cannot be restarted");
        }
        if (started.get()) {
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            client = clientFactory.createClient();
            publishConnection = client.connect();
            subscribeConnection = client.connectPubSub();
            subscribeConnection.addListener(new RedisPubSubAdapter<>() {
                @Override
                public void message(String channel, String message) {
                    dispatchMessage(channel, message);
                }
            });

            RedisPubSubCommands<String, String> commands = subscribeConnection.sync();
            for (String channel : handlers.keySet()) {
                commands.subscribe(channel);
            }
        } catch (Exception e) {
            started.set(false);
            cleanup();
            throw new IllegalStateException("Failed to start RedisBus", e);
        }
    }

    @Override
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        stopped.set(true);
        cleanup();
        executor.shutdownNow();
    }

    @Override
    public void subscribe(String channel, RedisMessageHandler handler) {
        ensureStarted();
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        Objects.requireNonNull(handler, "handler");

        handlers.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(handler);
        subscribeConnection.sync().subscribe(channel);
    }

    @Override
    public void publish(String channel, InfraMessage message) {
        ensureStarted();
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        Objects.requireNonNull(message, "message");
        String payload = serialize(message);
        RedisCommands<String, String> commands = publishConnection.sync();
        commands.publish(channel, payload);
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    private void dispatchMessage(String channel, String json) {
        InfraMessage message;
        try {
            message = OBJECT_MAPPER.readValue(json, InfraMessage.class);
        } catch (IOException e) {
            return;
        }
        List<RedisMessageHandler> channelHandlers = new ArrayList<>(handlers.getOrDefault(channel, List.of()));
        for (RedisMessageHandler handler : channelHandlers) {
            executor.submit(() -> handler.onMessage(channel, message));
        }
    }

    private String serialize(InfraMessage message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize InfraMessage", e);
        }
    }

    private void ensureStarted() {
        if (!started.get()) {
            throw new IllegalStateException("RedisBus has not been started");
        }
    }

    private void cleanup() {
        try {
            if (publishConnection != null) {
                publishConnection.close();
            }
        } finally {
            try {
                if (subscribeConnection != null) {
                    subscribeConnection.close();
                }
            } finally {
                if (client != null) {
                    client.shutdown();
                }
            }
        }
    }
}
