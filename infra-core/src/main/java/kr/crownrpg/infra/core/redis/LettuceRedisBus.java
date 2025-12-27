package kr.crownrpg.infra.core.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import kr.crownrpg.infra.api.context.InfraContext;
import kr.crownrpg.infra.api.message.InfraMessage;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.api.redis.RedisMessageHandler;
import kr.crownrpg.infra.api.redis.RedisMessageRules;

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
    private final String environment;
    private final String serverId;
    private final Map<String, CopyOnWriteArrayList<RedisMessageHandler>> pendingSubscriptions = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final ExecutorService executor = createExecutor();

    private RedisClient client;
    private StatefulRedisConnection<String, String> publishConnection;
    private StatefulRedisPubSubConnection<String, String> subscribeConnection;

    public LettuceRedisBus(RedisClientFactory clientFactory, InfraContext context) {
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
        Objects.requireNonNull(context, "context");
        this.environment = context.environment();
        this.serverId = context.serverId();
    }

    @Override
    public void start() {
        if (stopped.get()) {
            throw new IllegalStateException("RedisBus가 종료되어 다시 시작할 수 없습니다");
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
                    // 버스는 계속 동작하고, 실패 이유만 기록한다.
                    LOGGER.log(Level.WARNING, "시작 시 채널 '" + channel + "' 구독에 실패했습니다", e);
                }
            }
        } catch (Exception e) {
            started.set(false);
            cleanup();
            throw new IllegalStateException("RedisBus 시작에 실패했습니다", e);
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
            throw new IllegalStateException("RedisBus가 종료되어 새로운 구독을 받을 수 없습니다");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel은 비워 둘 수 없습니다");
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
                // 버스는 계속 동작하며, 의도는 pendingSubscriptions에 남겨둔다.
                LOGGER.log(Level.WARNING, "채널 '" + channel + "' 구독에 실패했습니다", e);
            }
        }
    }

    @Override
    public void publish(String channel, InfraMessage message) {
        ensureStarted();
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel은 비워 둘 수 없습니다");
        }
        Objects.requireNonNull(message, "message");
        String payload = serialize(message);
        try {
            RedisCommands<String, String> commands = publishConnection.sync();
            commands.publish(channel, payload);
        } catch (Exception e) {
            // 발행 실패로 버스가 중단되지 않도록 한다.
            LOGGER.log(Level.WARNING, "채널 '" + channel + "'에 메시지 발행에 실패했습니다", e);
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
            LOGGER.log(Level.WARNING, "채널 '" + channel + "'에서 InfraMessage 역직렬화에 실패했습니다", e);
            return;
        }
        if (!RedisMessageRules.shouldProcess(message, environment, serverId)) {
            return;
        }
        List<RedisMessageHandler> channelHandlers = List.copyOf(pendingSubscriptions.getOrDefault(channel, new CopyOnWriteArrayList<>()));
        for (RedisMessageHandler handler : channelHandlers) {
            executor.submit(() -> {
                try {
                    handler.onMessage(channel, message);
                } catch (Exception e) {
                    // 핸들러 실패는 다른 핸들러나 버스 생명주기에 영향을 주지 않는다.
                    LOGGER.log(Level.WARNING, "채널 '" + channel + "'의 핸들러 실행 중 오류", e);
                }
            });
        }
    }

    private String serialize(InfraMessage message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("InfraMessage 직렬화에 실패했습니다", e);
        }
    }

    private void ensureStarted() {
        if (!started.get()) {
            throw new IllegalStateException("RedisBus가 아직 시작되지 않았습니다");
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
