package kr.crownrpg.infra.api.lifecycle;

/**
 * infra 초기화/종료를 통일된 방식으로 다루기 위한 계약.
 * Paper/Velocity 쪽 bootstrap이 이 규약을 통해 안전하게 종료 처리 가능.
 */
public interface InfraLifecycle {
    void start();
    void stop();
}
