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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.concurrent.atomic.AtomicLong;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(LettuceRedisBus.class);

    private final RedisClientFactory clientFactory;
    private final String environment;
    private final String serverId;
    private final RedisBusSettings settings;
    private final Map<String, CopyOnWriteArrayList<RedisMessageHandler>> pendingSubscriptions = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicLong callerRunsCount = new AtomicLong(0);
    private final AtomicLong queueSaturationWarnings = new AtomicLong(0);
    private final ExecutorService executor;

    private RedisClient client;
    private StatefulRedisConnection<String, String> publishConnection;
    private StatefulRedisPubSubConnection<String, String> subscribeConnection;

    public LettuceRedisBus(RedisClientFactory clientFactory, InfraContext context) {
        this(clientFactory, context, RedisBusSettings.defaults());
    }

    public LettuceRedisBus(RedisClientFactory clientFactory, InfraContext context, RedisBusSettings settings) {
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
        Objects.requireNonNull(context, "context");
        this.environment = context.environment();
        this.serverId = context.serverId();
        this.settings = Objects.requireNonNull(settings, "settings");
        this.executor = createExecutor(settings);
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

            for (String channel : pendingSubscriptions.keySet()) {
                subscribeWithRetry(channel);
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
            subscribeWithRetry(channel);
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
            LOGGER.warn("채널 '{}'에 메시지 발행에 실패했습니다", channel, e);
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
            LOGGER.warn("채널 '{}'에서 InfraMessage 역직렬화에 실패했습니다", channel, e);
            return;
        }
        if (!RedisMessageRules.shouldProcess(message, environment, serverId)) {
            return;
        }
        List<RedisMessageHandler> channelHandlers = List.copyOf(pendingSubscriptions.getOrDefault(channel, new CopyOnWriteArrayList<>()));
        for (RedisMessageHandler handler : channelHandlers) {
            executor.submit(() -> {
                long start = System.nanoTime();
                try {
                    handler.onMessage(channel, message);
                } catch (Exception e) {
                    // 핸들러 실패는 다른 핸들러나 버스 생명주기에 영향을 주지 않는다.
                    LOGGER.warn("채널 '{}'의 핸들러 실행 중 오류", channel, e);
                } finally {
                    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                    LOGGER.info("채널 '{}' 메시지 처리 완료 ({} ms)", channel, elapsedMs);
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

    private ExecutorService createExecutor(RedisBusSettings settings) {
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
                settings.executorPoolSize(),
                settings.executorPoolSize(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.executorQueueCapacity()),
                threadFactory,
                (task, executor) -> {
                    long totalCallerRuns = callerRunsCount.incrementAndGet();
                    long warnings = queueSaturationWarnings.incrementAndGet();
                    // 큐 포화 시 호출자 스레드에서 실행되며, WARN으로 노출해 관측 가능하게 한다.
                    LOGGER.warn("RedisBus 실행기 큐 포화 - CallerRuns 적용 (누적 {}회, 경고 {}회)", totalCallerRuns, warnings);
                    // 대량 메시지 시에도 버스가 완전히 멈추지 않도록 호출자 스레드에서 실행한다.
                    if (!executor.isShutdown()) {
                        task.run();
                    }
                }
        );
    }

    private void subscribeWithRetry(String channel) {
        RedisPubSubCommands<String, String> commands = subscribeConnection.sync();
        int attempts = 0;
        while (attempts < settings.subscribeRetryAttempts()) {
            attempts++;
            try {
                commands.subscribe(channel);
                return;
            } catch (Exception e) {
                LOGGER.warn("채널 '{}' 구독에 실패했습니다 (시도 {}/{})", channel, attempts, settings.subscribeRetryAttempts(), e);
                if (attempts < settings.subscribeRetryAttempts()) {
                    try {
                        Thread.sleep(settings.subscribeRetryDelay().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        LOGGER.error("채널 '{}' 구독에 모두 실패했습니다 - 설정과 네트워크 상태를 점검하십시오", channel);
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
