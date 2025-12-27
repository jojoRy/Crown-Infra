# CROWN-INFRA 구조 점검 보고서 (업데이트)

## ❗ 반드시 수정해야 하는 문제
* 없음. Redis PubSub는 시작 전/후 구독 기억 및 상한 있는 실행기로 보호되고, Netty 실시간 채널도 환경·서버·토큰 검증과 허용된 peer 등록 후에만 데이터가 흐른다.

## ⚠️ 주의가 필요한 설계
* ServiceRegistry 의존성은 `infra.require-service-registry`로 강제/우회가 가능하지만, CrownLib를 명시적으로 depend/softdepend에 선언하지 않으면 등록을 건너뛰거나 실패한다. 배포 시 depend 선언 또는 require 플래그를 환경에 맞게 설정해야 한다.【F:infra-paper/src/main/java/kr/crownrpg/infra/paper/bootstrap/InfraBootstrap.java†L26-L176】【F:infra-paper/src/main/java/kr/crownrpg/infra/paper/config/InfraConfig.java†L3-L26】【F:infra-paper/src/main/resources/config.yml†L1-L8】
* RedisBus 예외와 핸들러 오류가 JUL 로거로 남도록 보완했지만, 운영 모니터링/알림 연동은 여전히 필요하다. 큐 상한 초과(CallersRuns) 시 경고 로그를 수집할 수 있는 설정이 권장된다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/redis/LettuceRedisBus.java†L39-L209】
* Netty outbound 큐는 드롭-올드스트 정책을 로깅하지만, 대규모 실시간 기능 확장 시 메트릭·경보 연동으로 드롭/재연결 이벤트를 관측해야 한다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyRealtimeChannel.java†L20-L149】【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyClient.java†L27-L129】

## ✅ 현재 설계가 잘된 부분
* `infra-api`/`infra-core`/Binder 경계가 유지되고, 계약은 순수하게 남아 있다. Paper 바인더는 설정을 읽어 core 구현을 주입하고 라이프사이클을 일관되게 관리한다.【F:infra-paper/src/main/java/kr/crownrpg/infra/paper/bootstrap/InfraBootstrap.java†L45-L128】
* RedisBus는 pending 구독 구조로 start 전후에 채널을 안전하게 추가하며, 중복 핸들러를 억제하고 고정 크기 실행기로 폭주를 방지한다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/redis/LettuceRedisBus.java†L39-L209】
* Netty 실시간 채널은 HELLO/WELCOME/REJECT 핸드셰이크를 통해 환경·서버·토큰·허용 peer를 검증하고, 등록되지 않은 채널에서 오는 프레임을 모두 차단한다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/HandshakeHandler.java†L14-L188】【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyRealtimeChannel.java†L20-L149】
* 클라이언트 측 재연결(backoff)과 바운디드 outbound 큐(drop-oldest)가 추가되어 실시간 경로의 과부하와 유실을 제어한다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyClient.java†L27-L128】【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyRealtimeChannel.java†L20-L149】

## 🚫 절대 바꾸지 말아야 할 부분
* `infra-api`와 구현(`infra-core`)의 분리, Paper/Velocity 바인더의 대칭 구조는 유지해야 한다. 이는 플랫폼 의존성을 계약 밖으로 밀어내는 핵심 원칙이다.【F:infra-paper/src/main/java/kr/crownrpg/infra/paper/bootstrap/InfraBootstrap.java†L45-L128】
* CrownLib에 구현을 넣지 않고 ServiceRegistry를 통한 조회만 사용하는 현 구조는 지속해야 한다.【F:infra-paper/src/main/java/kr/crownrpg/infra/paper/bootstrap/InfraBootstrap.java†L26-L155】
* Redis 메시지 계약·환경/서버 필터링 우선 원칙을 유지해 기능 플러그인 간 상호 운용성을 보장해야 한다.【F:infra-api/src/main/java/kr/crownrpg/infra/api/redis/RedisMessageRules.java†L7-L38】

## 📌 최종 결론 요약
1. **실서비스 가능성:** PubSub와 실시간 채널의 인증·구독/큐 관리가 보강되어 **실서비스 투입이 가능**한 수준이다. 운영 관측(로그/메트릭)만 보강하면 된다.
2. **기능 플러그인 개발 착수:** 네트워크 경로가 안정화되었으므로 기능 플러그인 개발을 바로 시작해도 된다. 실시간 기능은 모니터링 지표와 함께 검증하는 것이 좋다.
3. **권장 작업 순서:**
   1) 운영 로거/메트릭 연동 및 ServiceRegistry 의존성 선언 명확화 →
   2) 통합 부하/내결함성 테스트(RedisBus + Netty 핸드셰이크/큐 드롭 모니터링) →
   3) 기능 플러그인 개발 및 실환경 셰이크다운 테스트.
