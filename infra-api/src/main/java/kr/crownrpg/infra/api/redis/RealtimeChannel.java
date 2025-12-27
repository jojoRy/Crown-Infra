package kr.crownrpg.infra.api.redis;

/**
 * Optional contract for high-performance realtime channels.
 */
public interface RealtimeChannel {

    void start();

    void stop();

    boolean isAvailable();

    void send(String targetNodeId, byte[] payload);
}
