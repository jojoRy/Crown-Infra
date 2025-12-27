package kr.crownrpg.infra.api.redis;

/**
 * Scope values used in the standardized Redis channel naming convention.
 * <p>
 * Scope meaning:
 * <ul>
 *     <li>{@link #PROXY}: messages published by Paper nodes and consumed by the Velocity proxy.</li>
 *     <li>{@link #PAPER}: messages published by the Velocity proxy and consumed by Paper nodes.</li>
 *     <li>{@link #BROADCAST}: messages intended for all nodes regardless of role.</li>
 * </ul>
 */
public enum RedisChannelScope {

    PROXY("proxy"),
    PAPER("paper"),
    BROADCAST("broadcast");

    private final String wireName;

    RedisChannelScope(String wireName) {
        this.wireName = wireName;
    }

    /**
     * Returns the lowercase channel scope segment used on the wire.
     */
    public String wireName() {
        return wireName;
    }
}
