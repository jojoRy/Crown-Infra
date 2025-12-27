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

    public RedisBusSettings(int executorPoolSize,
                            int executorQueueCapacity,
                            int subscribeRetryAttempts,
                            Duration subscribeRetryDelay) {
        this.executorPoolSize = Math.max(1, executorPoolSize);
        this.executorQueueCapacity = Math.max(1, executorQueueCapacity);
        this.subscribeRetryAttempts = Math.max(1, subscribeRetryAttempts);
        this.subscribeRetryDelay = subscribeRetryDelay.isNegative() ? Duration.ZERO : subscribeRetryDelay;
    }

    public static RedisBusSettings defaults() {
        return new RedisBusSettings(4, 256, 3, Duration.ofMillis(200));
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
}
