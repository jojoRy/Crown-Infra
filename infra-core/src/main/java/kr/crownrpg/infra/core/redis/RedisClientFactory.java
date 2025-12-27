package kr.crownrpg.infra.core.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

import java.time.Duration;
import java.util.Objects;

/**
 * Factory responsible for creating configured Lettuce {@link RedisClient} instances.
 */
public final class RedisClientFactory {

    private final String host;
    private final int port;
    private final boolean useSsl;
    private final String password;
    private final Duration timeout;
    private final int database;

    public RedisClientFactory(String host, int port, boolean useSsl, String password, Duration timeout, int database) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.useSsl = useSsl;
        this.password = password;
        this.timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        this.database = database;
    }

    public RedisClient createClient() {
        return RedisClient.create(createUri());
    }

    public RedisURI createUri() {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withDatabase(database)
                .withTimeout(timeout);

        if (useSsl) {
            builder.withSsl(true);
        }

        if (password != null && !password.isEmpty()) {
            builder.withPassword(password.toCharArray());
        }

        return builder.build();
    }
}
