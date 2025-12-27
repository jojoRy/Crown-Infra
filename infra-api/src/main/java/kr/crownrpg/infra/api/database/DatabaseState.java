package kr.crownrpg.infra.api.database;

/**
 * 데이터베이스 연결 상태 머신.
 */
public enum DatabaseState {
    STOPPED,
    CONNECTING,
    RUNNING,
    DEGRADED
}
