package kr.crownrpg.infra.api.redis;

/**
 * 실시간 채널 상태 머신.
 */
public enum RealtimeChannelState {
    STOPPED,
    CONNECTING,
    RUNNING,
    DEGRADED
}
