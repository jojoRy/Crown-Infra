package kr.crownrpg.infra.api.context;

/**
 * 모든 메시지/로깅/환경 분기에 사용되는 "서버 컨텍스트".
 * - environment: prod / dev / staging 등
 * - serverId: village-1, dungeon-2, proxy-1 ...
 * - kind: PAPER / VELOCITY
 */
public interface InfraContext {
    String environment();
    String serverId();
    ServerKind kind();
}
