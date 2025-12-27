package kr.crownrpg.infra.core.redis;

import java.time.Duration;

/**
 * RedisBus 실행 정책을 외부 설정으로 전달하기 위한 옵션.
 */
public final class RedisBusSettings {

    private final int executorPoolSize;
    private final int executorQueueCapacity;
    private final int subscribeRetryAttempts;
    private final Duration subscribeRetryDelay;
    private final Duration reconnectInitialDelay;
    private final Duration reconnectMaxDelay;
    private final int dropWarnThreshold;
    private final Duration degradedLogInterval;

    public RedisBusSettings(int executorPoolSize,
                            int executorQueueCapacity,
                            int subscribeRetryAttempts,
                            Duration subscribeRetryDelay,
                            Duration reconnectInitialDelay,
                            Duration reconnectMaxDelay,
                            int dropWarnThreshold,
                            Duration degradedLogInterval) {
        this.executorPoolSize = Math.max(1, executorPoolSize);
        this.executorQueueCapacity = Math.max(1, executorQueueCapacity);
        this.subscribeRetryAttempts = Math.max(1, subscribeRetryAttempts);
        this.subscribeRetryDelay = subscribeRetryDelay.isNegative() ? Duration.ZERO : subscribeRetryDelay;
        this.reconnectInitialDelay = reconnectInitialDelay.isNegative() ? Duration.ZERO : reconnectInitialDelay;
        this.reconnectMaxDelay = reconnectMaxDelay.isNegative() ? Duration.ZERO : reconnectMaxDelay;
        this.dropWarnThreshold = Math.max(1, dropWarnThreshold);
        this.degradedLogInterval = degradedLogInterval.isNegative() ? Duration.ZERO : degradedLogInterval;
    }

    public static RedisBusSettings defaults() {
        return new RedisBusSettings(4, 256, 3, Duration.ofMillis(200), Duration.ofMillis(500), Duration.ofSeconds(15), 20, Duration.ofSeconds(5));
    }

    public int executorPoolSize() {
        return executorPoolSize;
    }

    public int executorQueueCapacity() {
        return executorQueueCapacity;
    }

    public int subscribeRetryAttempts() {
        return subscribeRetryAttempts;
    }

    public Duration subscribeRetryDelay() {
        return subscribeRetryDelay;
    }

    public Duration reconnectInitialDelay() {
        return reconnectInitialDelay;
    }

    public Duration reconnectMaxDelay() {
        return reconnectMaxDelay;
    }

    public int dropWarnThreshold() {
        return dropWarnThreshold;
    }

    public Duration degradedLogInterval() {
        return degradedLogInterval;
    }
}
