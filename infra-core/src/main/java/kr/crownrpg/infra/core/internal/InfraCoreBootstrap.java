package kr.crownrpg.infra.core.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import kr.crownrpg.infra.api.context.InfraContext;
import kr.crownrpg.infra.api.redis.RedisBus;
import kr.crownrpg.infra.core.redis.LettuceRedisBus;
import kr.crownrpg.infra.core.redis.RedisClientFactory;
import kr.crownrpg.infra.core.redis.RedisCodec;

import java.util.Objects;

/**
 * 플랫폼(Paper/Velocity)에서 호출하는 "코어 조립" 클래스.
 * - infra-core의 구체 구현을 여기서 다 조립해서 반환
 * - paper/velocity는 이걸 호출만 하면 됨
 */
public final class InfraCoreBootstrap {

    private final InfraContext context;
    private final RedisClientFactory.RedisConnectionSpec redisSpec;

    private LettuceRedisBus redisBus;

    public InfraCoreBootstrap(InfraContext context, RedisClientFactory.RedisConnectionSpec redisSpec) {
        this.context = Objects.requireNonNull(context, "context");
        this.redisSpec = Objects.requireNonNull(redisSpec, "redisSpec");
    }

    public void start() {
        // Jackson은 core에서 고정 세팅 가능(필요시 v2에서 modules 추가)
        ObjectMapper mapper = new ObjectMapper();
        RedisCodec codec = new RedisCodec(mapper);

        RedisClient client = RedisClientFactory.create(redisSpec);
        this.redisBus = new LettuceRedisBus(client, codec);
    }

    public RedisBus redisBus() {
        if (redisBus == null) throw new IllegalStateException("InfraCoreBootstrap not started");
        return redisBus;
    }

    public void stop() {
        if (redisBus != null) {
            redisBus.shutdown();
            redisBus = null;
        }
    }

    public InfraContext context() {
        return context;
    }
}
