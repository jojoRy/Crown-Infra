package kr.crownrpg.infra.api.message;

/**
 * 메시지 타입은 "문자열" 기반으로 운용하되,
 * 플러그인마다 enum을 따로 만들어도 되도록 API는 String을 허용한다.
 *
 * 이 enum은 Infra에서 공통으로 자주 쓰는 타입(예시)만 제공.
 * 실제 서비스별 타입은 각 플러그인에서 별도 정의 권장.
 */
public enum MessageType {
    // 예시 (필요하면 삭제/추가 가능)
    GLOBAL_CHAT,
    PARTY_EVENT,
    GUILD_EVENT,
    ECONOMY_EVENT,

    // 인프라 자체 신호
    HEARTBEAT
}
