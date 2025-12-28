package kr.crownrpg.infra.api.redis;

import kr.crownrpg.infra.api.lifecycle.ManagedLifecycle;
import kr.crownrpg.infra.api.message.InfraMessage;

/**
 * Redis 기반(또는 호환 구현체) 퍼블리시/서브스크라이브 버스 계약.
 * <p>
 * 메시지 브로커 역할을 수행하며, 시작/중단/구독/발행 수명주기를 명확히 정의한다.
 */
public interface RedisBus extends ManagedLifecycle, AutoCloseable {

    @Override
    void start();

    @Override
    void stop();

    /**
     * 지정한 채널에 대한 메시지 핸들러를 등록한다.
     * 채널 이름이 비어 있지 않은지, 핸들러가 null이 아닌지 구현체에서 검증해야 한다.
     */
    void subscribe(String channel, RedisMessageHandler handler);

    /**
     * 주어진 채널로 {@link InfraMessage}를 직렬화하여 발행한다.
     */
    void publish(String channel, InfraMessage message);

    boolean isStarted();

    RedisBusState state();

    default boolean isRunning() {
        return state() == RedisBusState.RUNNING;
    }

    default boolean isDegraded() {
        return state() == RedisBusState.DEGRADED;
    }

    @Override
    default void close() {
        stop();
    }
}
