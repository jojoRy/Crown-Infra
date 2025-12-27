package kr.crownrpg.infra.paper.binder;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.io.Closeable;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class RedisBinder implements Closeable {

    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private StatefulRedisPubSubConnection<String, String> pubSub;

    public void start(Map<String, Object> redisConfig) {
        Objects.requireNonNull(redisConfig, "redisConfig");

        String host = str(redisConfig.get("host"), "127.0.0.1");
        int port = intVal(redisConfig.get("port"), 6379);
        boolean ssl = bool(redisConfig.get("ssl"), false);
        String password = str(redisConfig.get("password"), "");
        long timeoutMs = longVal(redisConfig.get("timeout-ms"), 3000L);
        String clientName = str(redisConfig.get("client-name"), "CrownInfra-Paper");

        RedisURI.Builder builder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withSsl(ssl)
                .withTimeout(Duration.ofMillis(timeoutMs));

        if (!password.isBlank()) {
            builder.withPassword(password.toCharArray());
        }

        RedisURI uri = builder.build();

        RedisClient rc = RedisClient.create(uri);
        rc.setOptions(ClientOptions.builder()
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(timeoutMs)))
                .build());

        StatefulRedisConnection<String, String> conn = rc.connect();
        StatefulRedisPubSubConnection<String, String> ps = rc.connectPubSub();

        // client name (가능한 경우만)
        try {
            RedisCommands<String, String> sync = conn.sync();
            sync.clientSetname(clientName);
        } catch (Throwable ignore) {}

        // ping check
        String pong = conn.sync().ping();
        if (!"PONG".equalsIgnoreCase(pong)) {
            ps.close();
            conn.close();
            rc.shutdown();
            throw new IllegalStateException("Redis ping failed: " + pong);
        }

        this.client = rc;
        this.connection = conn;
        this.pubSub = ps;
    }

    public StatefulRedisConnection<String, String> connection() {
        if (connection == null) {
            throw new IllegalStateException("RedisBinder not started");
        }
        return connection;
    }

    public StatefulRedisPubSubConnection<String, String> pubSub() {
        if (pubSub == null) {
            throw new IllegalStateException("RedisBinder not started");
        }
        return pubSub;
    }

    @Override
    public void close() {
        // ⚠️ 순서 중요: pubsub → connection → client
        try {
            if (pubSub != null) pubSub.close();
        } catch (Throwable ignore) {}

        try {
            if (connection != null) connection.close();
        } catch (Throwable ignore) {}

        try {
            if (client != null) client.shutdown();
        } catch (Throwable ignore) {}

        pubSub = null;
        connection = null;
        client = null;
    }

    /* ---------- util ---------- */

    private static String str(Object v, String def) {
        if (v == null) return def;
        return String.valueOf(v);
    }

    private static int intVal(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private static long longVal(Object v, long def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private static boolean bool(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
