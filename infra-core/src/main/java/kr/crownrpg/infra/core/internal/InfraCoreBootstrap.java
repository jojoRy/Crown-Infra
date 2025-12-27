package kr.crownrpg.infra.core.internal;

import kr.crownrpg.infra.api.context.InfraContext;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.core.redis.LettuceRedisBus;
import kr.crownrpg.infra.core.redis.RedisClientFactory;

import java.util.Objects;

/**
 * 플랫폼(Paper/Velocity)에서 호출하는 "코어 조립" 클래스.
 * - infra-core의 구체 구현을 여기서 다 조립해서 반환
 * - paper/velocity는 이걸 호출만 하면 됨
 */
public final class InfraCoreBootstrap {

    private final InfraContext context;
    private final RedisClientFactory redisFactory;

    private LettuceRedisBus redisBus;

    public InfraCoreBootstrap(InfraContext context, RedisClientFactory redisFactory) {
        this.context = Objects.requireNonNull(context, "context");
        this.redisFactory = Objects.requireNonNull(redisFactory, "redisFactory");
    }

    public void start() {
        this.redisBus = new LettuceRedisBus(redisFactory);
        this.redisBus.start();
    }

    public RedisBus redisBus() {
        if (redisBus == null) throw new IllegalStateException("InfraCoreBootstrap not started");
        return redisBus;
    }

    public void stop() {
        if (redisBus != null) {
            redisBus.stop();
            redisBus = null;
        }
    }

    public InfraContext context() {
        return context;
    }
}
