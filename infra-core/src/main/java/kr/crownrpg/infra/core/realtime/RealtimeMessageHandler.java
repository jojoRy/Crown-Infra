package kr.crownrpg.infra.core.realtime;

/**
 * Callback for delivering realtime messages after routing.
 */
@FunctionalInterface
public interface RealtimeMessageHandler {

    void onMessage(String fromServerId, byte[] payload);
}
