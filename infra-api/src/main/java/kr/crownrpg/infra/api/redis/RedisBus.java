package kr.crownrpg.infra.api.redis;

import kr.crownrpg.infra.api.lifecycle.ManagedLifecycle;
import kr.crownrpg.infra.api.message.InfraMessage;

/**
 * Contract for publish/subscribe bus backed by Redis or other implementations.
 */
public interface RedisBus extends ManagedLifecycle, AutoCloseable {

    @Override
    void start();

    @Override
    void stop();

    void subscribe(String channel, RedisMessageHandler handler);

    void publish(String channel, InfraMessage message);

    boolean isStarted();

    @Override
    default void close() {
        stop();
    }
}
