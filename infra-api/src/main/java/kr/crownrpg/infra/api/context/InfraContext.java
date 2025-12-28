package kr.crownrpg.infra.api.context;

import kr.crownrpg.infra.api.Preconditions;

/**
 * 환경 이름과 서버 식별자를 보관하는 변경 불가 컨텍스트.
 * <p>
 * 모든 플랫폼에서 공통으로 넘겨받는 최소한의 실행 정보를 담으며,
 * 문자열이 비어 있지 않은지 초기화 시점에 검증한다.
 */
public record InfraContext(String environment, String serverId) {

    public InfraContext {
        Preconditions.checkNotBlank(environment, "environment");
        Preconditions.checkNotBlank(serverId, "serverId");
    }

    /**
     * 정적 팩토리 메서드로, 호출부에서 생성자를 직접 호출하는 대신 의도를 명확히 표현한다.
     */
    public static InfraContext of(String environment, String serverId) {
        return new InfraContext(environment, serverId);
    }

    @Override
    public String toString() {
        return "InfraContext{" +
                "environment='" + environment + '\'' +
                ", serverId='" + serverId + '\'' +
                '}';
    }
}
