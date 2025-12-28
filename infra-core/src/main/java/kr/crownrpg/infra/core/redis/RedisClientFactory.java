package kr.crownrpg.infra.core.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

import java.time.Duration;
import java.util.Objects;

/**
 * 설정 값을 받아 Lettuce {@link RedisClient}를 생성하는 팩토리.
 * <p>
 * 호스트/포트/SSL/비밀번호/타임아웃/DB 인덱스 등의 연결 정보를 한 곳에 모아
 * 실제 클라이언트와 URI를 일관되게 빌드하는 역할을 한다.
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

    /**
     * 현재 설정으로 새 Redis 클라이언트를 생성한다.
     */
    public RedisClient createClient() {
        return RedisClient.create(createUri());
    }

    /**
     * Lettuce에서 사용 가능한 {@link RedisURI}를 조립한다.
     */
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
