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
import kr.crownrpg.infra.api.redis.RedisBusState;
import kr.crownrpg.infra.api.redis.RedisMessageHandler;
import kr.crownrpg.infra.api.redis.RedisMessageRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis Pub/Sub 자동 복구 버스.
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
    private final AtomicReference<RedisBusState> state = new AtomicReference<>(RedisBusState.STOPPED);
    private final AtomicLong droppedPublishCount = new AtomicLong(0);
    private final AtomicLong droppedInboundCount = new AtomicLong(0);
    private final AtomicLong lastDegradedLogMillis = new AtomicLong(0);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final ExecutorService executor;
    private final ScheduledExecutorService reconnectScheduler;

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
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("lettuce-redis-reconnect-" + sequence.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public void start() {
        if (stopped.get()) {
            LOGGER.warn("RedisBus가 완전히 중단된 상태여서 재시작할 수 없습니다");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        transitionState(RedisBusState.CONNECTING, "Redis Pub/Sub 연결을 시작합니다");
        scheduleReconnect(Duration.ZERO);
    }

    @Override
    public void stop() {
        if (!started.getAndSet(false)) {
            return;
        }
        stopped.set(true);
        transitionState(RedisBusState.STOPPED, "Redis Pub/Sub를 종료합니다");
        reconnectScheduler.shutdownNow();
        cleanup();
        executor.shutdownNow();
    }

    @Override
    public RedisBusState state() {
        return state.get();
    }

    @Override
    public void subscribe(String channel, RedisMessageHandler handler) {
        if (stopped.get()) {
            LOGGER.warn("RedisBus가 종료되어 새로운 구독을 받을 수 없습니다");
            return;
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

        if (started.get() && channelWasEmpty && state.get() == RedisBusState.RUNNING) {
            subscribeWithRetry(channel);
        }
    }

    @Override
    public void publish(String channel, InfraMessage message) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel은 비워 둘 수 없습니다");
        }
        Objects.requireNonNull(message, "message");
        if (state.get() != RedisBusState.RUNNING) {
            logDrop(droppedPublishCount, "Redis가 DEGRADED 상태여서 발행을 드롭합니다");
            return;
        }
        String payload = serialize(message);
        try {
            RedisCommands<String, String> commands = publishConnection.sync();
            commands.publish(channel, payload);
        } catch (Exception e) {
            handleFailure("채널 '" + channel + "' 발행 실패", e);
        }
    }

    @Override
    public boolean isStarted() {
        return started.get();
    }

    private void dispatchMessage(String channel, String json) {
        if (state.get() != RedisBusState.RUNNING) {
            logDrop(droppedInboundCount, "Redis가 DEGRADED 상태여서 수신 메시지를 드롭합니다");
            return;
        }
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
                    LOGGER.warn("채널 '{}'의 핸들러 실행 중 오류", channel, e);
                } finally {
                    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                    if (elapsedMs > 500) {
                        LOGGER.warn("채널 '{}' 메시지 처리 지연 {} ms", channel, elapsedMs);
                    } else {
                        LOGGER.info("채널 '{}' 메시지 처리 완료 ({} ms)", channel, elapsedMs);
                    }
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
                    LOGGER.warn("RedisBus 실행기 큐 포화 - CallerRuns 적용 (누적 {}회, 경고 {}회)", totalCallerRuns, warnings);
                    if (!executor.isShutdown()) {
                        task.run();
                    }
                }
        );
    }

    private void scheduleReconnect(Duration delay) {
        if (stopped.get()) {
            return;
        }
        reconnectScheduler.schedule(this::attemptConnect, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void attemptConnect() {
        if (stopped.get()) {
            return;
        }
        transitionState(RedisBusState.CONNECTING, "Redis Pub/Sub 연결을 시도합니다");
        try {
            cleanup();
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
            reconnectAttempts.set(0);
            transitionState(RedisBusState.RUNNING, "Redis Pub/Sub 연결이 정상화되었습니다");
        } catch (Exception e) {
            handleFailure("Redis 연결 실패", e);
        }
    }

    private void subscribeWithRetry(String channel) {
        if (subscribeConnection == null) {
            handleFailure("구독 채널이 초기화되지 않았습니다", null);
            return;
        }
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
        LOGGER.warn("채널 '{}' 구독에 모두 실패했습니다 - 자동 복구를 재시도합니다", channel);
        handleFailure("채널 구독 실패", null);
    }

    private void handleFailure(String reason, Exception cause) {
        transitionState(RedisBusState.DEGRADED, reason);
        if (cause != null) {
            logDegraded(reason, cause);
        }
        Duration delay = nextReconnectDelay();
        scheduleReconnect(delay);
    }

    private Duration nextReconnectDelay() {
        int attempt = reconnectAttempts.incrementAndGet();
        long backoff = (long) (settings.reconnectInitialDelay().toMillis() * Math.pow(2, Math.max(0, attempt - 1)));
        long clamped = Math.min(backoff, settings.reconnectMaxDelay().toMillis());
        return Duration.ofMillis(clamped);
    }

    private void logDrop(AtomicLong counter, String message) {
        long total = counter.incrementAndGet();
        if (total % settings.dropWarnThreshold() == 0) {
            LOGGER.warn("{} (누적 {}회)", message, total);
        }
    }

    private void logDegraded(String reason, Exception cause) {
        long now = System.currentTimeMillis();
        long lastLog = lastDegradedLogMillis.get();
        if (now - lastLog < settings.degradedLogInterval().toMillis()) {
            return;
        }
        if (lastDegradedLogMillis.compareAndSet(lastLog, now)) {
            if (cause == null) {
                LOGGER.warn("Redis 장애 감지: {}", reason);
            } else {
                LOGGER.warn("Redis 장애 감지: {}", reason, cause);
            }
        }
    }

    private void transitionState(RedisBusState newState, String message) {
        RedisBusState previous = state.getAndSet(newState);
        if (previous == newState) {
            return;
        }
        switch (newState) {
            case RUNNING -> LOGGER.info("{}", message);
            case DEGRADED -> LOGGER.warn("{} (DEGRADED)", message);
            case CONNECTING -> LOGGER.info("{}", message);
            case STOPPED -> LOGGER.info("{}", message);
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
