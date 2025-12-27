package kr.crownrpg.infra.core.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

import java.time.Duration;
import java.util.Objects;

/**
 * Lettuce RedisClient 생성 책임만 분리.
 * - Paper/Velocity 어디서든 동일하게 사용.
 */
public final class RedisClientFactory {

    private RedisClientFactory() {}

    public static RedisClient create(RedisConnectionSpec spec) {
        Objects.requireNonNull(spec, "spec");

        RedisURI.Builder b = RedisURI.builder()
                .withHost(requireNotBlank(spec.host(), "host"))
                .withPort(spec.port());

        if (spec.ssl()) b.withSsl(true);
        if (spec.timeout() != null) b.withTimeout(spec.timeout());
        if (spec.password() != null && !spec.password().isBlank()) {
            b.withPassword(spec.password().toCharArray());
        }
        if (spec.database() >= 0) {
            b.withDatabase(spec.database());
        }

        return RedisClient.create(b.build());
    }

    private static String requireNotBlank(String v, String name) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("redis." + name + " is blank");
        return v;
    }

    /**
     * infra-api는 config를 모르니까, core에서 최소 연결 스펙을 정의.
     * (paper/velocity 쪽 config loader가 이걸 만들어서 넘겨주면 됨)
     */
    public record RedisConnectionSpec(
            String host,
            int port,
            boolean ssl,
            String password,
            int database,
            Duration timeout
    ) {
        public RedisConnectionSpec {
            if (port <= 0) throw new IllegalArgumentException("redis.port must be > 0");
        }

        public static RedisConnectionSpec of(String host, int port, boolean ssl, String password) {
            return new RedisConnectionSpec(host, port, ssl, password, 0, Duration.ofSeconds(5));
        }
    }
}
