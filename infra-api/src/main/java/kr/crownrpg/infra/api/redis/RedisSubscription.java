package kr.crownrpg.infra.api.redis;

import java.io.Closeable;

/**
 * 구독 핸들.
 * close() 하면 해당 구독 해제.
 */
public interface RedisSubscription extends Closeable {
    String channel();

    @Override
    void close();
}
