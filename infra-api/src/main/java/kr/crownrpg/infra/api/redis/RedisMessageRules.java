package kr.crownrpg.infra.api.redis;

import kr.crownrpg.infra.api.Preconditions;
import kr.crownrpg.infra.api.message.InfraMessage;

/**
 * Contract-level rules that every Redis PubSub consumer must respect when handling {@link InfraMessage} events.
 * <p>
 * Required guards before processing a message:
 * <pre>{@code
 * if (!message.environment().equals(selfEnvironment)) return; // ignore other environments
 * if (message.fromServerId().equals(selfServerId)) return;   // ignore messages from self
 * }</pre>
 * Implementations should apply these checks immediately after deserialization and before any side effects.
 */
public final class RedisMessageRules {

    private RedisMessageRules() {
    }

    /**
     * Returns {@code true} if the message should be processed by the current node, following the required
     * environment/self-origin guards.
     *
     * @param message         deserialized message
     * @param selfEnvironment current node environment (must match message environment)
     * @param selfServerId    current node server id (must differ from message origin)
     * @return true when the message passes guard checks
     */
    public static boolean shouldProcess(InfraMessage message, String selfEnvironment, String selfServerId) {
        Preconditions.checkNotNull(message, "message");
        Preconditions.checkNotBlank(selfEnvironment, "selfEnvironment");
        Preconditions.checkNotBlank(selfServerId, "selfServerId");
        if (!message.environment().equals(selfEnvironment)) {
            return false;
        }
        return !message.fromServerId().equals(selfServerId);
    }
}
