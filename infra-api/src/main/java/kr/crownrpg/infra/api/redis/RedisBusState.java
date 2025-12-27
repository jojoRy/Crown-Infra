package kr.crownrpg.infra.api.redis;

/**
 * Redis Pub/Sub 상태 머신.
 */
public enum RedisBusState {
    STOPPED,
    CONNECTING,
    RUNNING,
    DEGRADED
}
