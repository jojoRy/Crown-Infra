package kr.crownrpg.infra.core.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.StatefulRedisPubSubConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import kr.crownrpg.infra.api.message.InfraMessage;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.api.redis.RedisMessageHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RedisBus implementation backed by Lettuce Pub/Sub.
 * <p>
 * Behavior notes:
 * - subscribe() is allowed both before and after start(); pending subscriptions are remembered and applied on start.
 * - Duplicate handler registration per channel is ignored and does not trigger re-subscription.
 * - Executor is bounded to avoid unbounded thread creation; handlers are wrapped with exception guards so the bus keeps running.
 */
public class LettuceRedisBus implements RedisBus {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(LettuceRedisBus.class.getName());
    private static final int EXECUTOR_POOL_SIZE = 4;
    private static final int EXECUTOR_QUEUE_CAPACITY = 256;

    private final RedisClientFactory clientFactory;
    private final Map<String, CopyOnWriteArrayList<RedisMessageHandler>> pendingSubscriptions = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final ExecutorService executor = createExecutor();

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
            subscribeConnection.addListener(new RedisPubSubListener<>() {
                @Override
                public void message(String channel, String message) {
                    dispatchMessage(channel, message);
                }

                @Override
                public void message(String pattern, String channel, String message) {
                    dispatchMessage(channel, message);
                }

                @Override
                public void subscribed(String channel, long count) {
                }

                @Override
                public void psubscribed(String pattern, long count) {
                }

                @Override
                public void unsubscribed(String channel, long count) {
                }

                @Override
                public void punsubscribed(String pattern, long count) {
                }
            });

            RedisPubSubCommands<String, String> commands = subscribeConnection.sync();
            for (String channel : pendingSubscriptions.keySet()) {
                try {
                    commands.subscribe(channel);
                } catch (Exception e) {
                    // Keep bus alive; log to stderr for visibility.
                    LOGGER.log(Level.WARNING, "Failed to subscribe channel '" + channel + "' on start", e);
                }
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
        if (stopped.get()) {
            throw new IllegalStateException("RedisBus has been stopped and cannot accept new subscriptions");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        Objects.requireNonNull(handler, "handler");

        CopyOnWriteArrayList<RedisMessageHandler> handlers = pendingSubscriptions.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>());
        boolean channelWasEmpty = handlers.isEmpty();
        if (!handlers.contains(handler)) {
            handlers.add(handler);
        }

        if (started.get() && channelWasEmpty) {
            try {
                subscribeConnection.sync().subscribe(channel);
            } catch (Exception e) {
                // Keep bus alive; pendingSubscriptions keeps the intent so it can be retried on restart.
                LOGGER.log(Level.WARNING, "Failed to subscribe channel '" + channel + "'", e);
            }
        }
    }

    @Override
    public void publish(String channel, InfraMessage message) {
        ensureStarted();
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        Objects.requireNonNull(message, "message");
        String payload = serialize(message);
        try {
            RedisCommands<String, String> commands = publishConnection.sync();
            commands.publish(channel, payload);
        } catch (Exception e) {
            // Prevent bus termination on publish failures.
            LOGGER.log(Level.WARNING, "Failed to publish message to channel '" + channel + "'", e);
        }
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
            LOGGER.log(Level.WARNING, "Failed to deserialize InfraMessage from channel '" + channel + "'", e);
            return;
        }
        List<RedisMessageHandler> channelHandlers = List.copyOf(pendingSubscriptions.getOrDefault(channel, new CopyOnWriteArrayList<>()));
        for (RedisMessageHandler handler : channelHandlers) {
            executor.submit(() -> {
                try {
                    handler.onMessage(channel, message);
                } catch (Exception e) {
                    // Handler failure should not affect other handlers or the bus lifecycle.
                    LOGGER.log(Level.WARNING, "Handler error on channel '" + channel + "'", e);
                }
            });
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

    private ExecutorService createExecutor() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("lettuce-redis-bus-" + sequence.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
        return new ThreadPoolExecutor(
                EXECUTOR_POOL_SIZE,
                EXECUTOR_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(EXECUTOR_QUEUE_CAPACITY),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
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
