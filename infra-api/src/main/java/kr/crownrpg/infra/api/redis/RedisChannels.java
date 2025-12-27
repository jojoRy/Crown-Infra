package kr.crownrpg.infra.api.redis;

import kr.crownrpg.infra.api.Preconditions;

/**
 * Single source of truth for Redis PubSub channel naming in Crown Infra.
 * <p>
 * Channel format: {@code crown:{environment}:{scope}} where:
 * <ul>
 *     <li>{@code environment}: the logical environment name (e.g., production, staging) that isolates traffic.</li>
 *     <li>{@code scope}: {@link RedisChannelScope#PROXY proxy}, {@link RedisChannelScope#PAPER paper}, or
 *     {@link RedisChannelScope#BROADCAST broadcast}.</li>
 * </ul>
 * <p>
 * Message flow rules (must be respected by implementations and consumers):
 * <ul>
 *     <li>Paper → {@code crown:{env}:proxy}</li>
 *     <li>Velocity → {@code crown:{env}:paper}</li>
 *     <li>Broadcast to all nodes → {@code crown:{env}:broadcast}</li>
 * </ul>
 */
public final class RedisChannels {

    private static final String PREFIX = "crown";

    private RedisChannels() {
    }

    public static String forProxy(String environment) {
        return channel(environment, RedisChannelScope.PROXY);
    }

    public static String forPaper(String environment) {
        return channel(environment, RedisChannelScope.PAPER);
    }

    public static String forBroadcast(String environment) {
        return channel(environment, RedisChannelScope.BROADCAST);
    }

    public static String channel(String environment, RedisChannelScope scope) {
        Preconditions.checkNotBlank(environment, "environment");
        Preconditions.checkNotNull(scope, "scope");
        return PREFIX + ":" + environment + ":" + scope.wireName();
    }
}
