package kr.crownrpg.infra.api.context;

/**
 * 어떤 런타임에서 실행 중인지 구분.
 * - PAPER: Paper 서버
 * - VELOCITY: Velocity 프록시
 */
public enum ServerKind {
    PAPER,
    VELOCITY
}
