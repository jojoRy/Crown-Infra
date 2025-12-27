package kr.crownrpg.infra.api.redis;

/**
 * Optional contract for high-performance realtime channels.
 */
public interface RealtimeChannel {

    void start();

    void stop();

    boolean isAvailable();

    RealtimeChannelState state();

    default boolean isRunning() {
        return state() == RealtimeChannelState.RUNNING;
    }

    default boolean isDegraded() {
        return state() == RealtimeChannelState.DEGRADED;
    }

    void send(String targetNodeId, byte[] payload);
}
