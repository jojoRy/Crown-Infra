# CROWN-INFRA 구조 점검 보고서 (최신)

## 1️⃣ 반드시 수정해야 할 치명적 문제
* 현재 코드에서 즉시 수정이 필요한 크래시/누수/이중 시작·종료 문제는 발견되지 않았음. RedisBus, Netty 채널, Hikari DataSource 모두 시작/중지 가드와 정리 루틴을 포함하고 있다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/redis/LettuceRedisBus.java†L60-L205】【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyRealtimeChannel.java†L40-L150】【F:infra-core/src/main/java/kr/crownrpg/infra/core/database/HikariDatabaseService.java†L23-L137】

## 2️⃣ 미래에 위험해질 수 있는 설계
### ▪ Redis PubSub
* 실행기 풀(4 threads)·큐(256)가 고정이라, 장기간 피크 트래픽 시 CallerRuns 정책으로 메시지 처리 지연/역류가 발생할 수 있다. 동적 확장이나 메트릭 기반 경보가 필요하다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/redis/LettuceRedisBus.java†L37-L205】
* 채널 구독은 start 전후 모두 pending 구조로 관리하지만, 구독 실패 시 재시도 로직이 없어 일시적 장애 후 복구가 지연될 수 있다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/redis/LettuceRedisBus.java†L60-L159】

### ▪ Netty 실시간 채널
* 클라이언트 재연결은 최대 30s 지수 백오프 후 재시작하지만, 반복 실패 시 backpressure 모니터링/알림이 없어 무한 재시도 루프가 길게 이어질 수 있다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyClient.java†L68-L161】
* outbound 큐는 512개로 고정이며 초과 시 가장 오래된 메시지를 드롭한다. 대규모 채팅/공지 시 손실이 증가할 수 있으나, 드롭 건수를 외부로 노출하는 경량 메트릭이 없다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyRealtimeChannel.java†L40-L150】
* 허용 peer set은 정적(Set.copyOf). 운영 중 서버 증감 시 런타임 추가 구성이 없어 재배포 없이 확장하기 어렵다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyRealtimeChannel.java†L22-L74】

### ▪ Database
* 단일 Hikari 풀을 전 플러그인에서 공유하는 구조라, 트랜잭션 경계를 긴 작업이 잠식하면 다른 기능 플러그인이 대기하게 된다. 장기 쿼리/배치에 대한 타임아웃·모니터링이 필요하다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/database/HikariDatabaseService.java†L32-L137】
* JDBC URL 외 스키마 격리나 네임스페이스 가드가 없으므로, 여러 기능 플러그인이 동일 DB를 사용할 때 테이블 충돌/DDL 오염 위험이 있다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/database/HikariDatabaseService.java†L23-L137】

### ▪ Binder 구조
* Paper/Velocity 양쪽 바인더가 환경·serverId를 컨피그에서 읽어 동일한 컨텍스트를 구성하지만, ServiceRegistry 의존을 선택적으로 건너뛸 수 있어( require 플래그) 환경별 배포가 혼재하면 데이터 오염을 탐지하기 어렵다.【F:infra-paper/src/main/java/kr/crownrpg/infra/paper/bootstrap/InfraBootstrap.java†L37-L134】
* 기능이 늘어날수록 바인더가 리소스 초기화/등록/라우팅을 모두 맡아 비대해질 가능성이 있다. API 계약을 유지하면서 역할을 서브 모듈로 분리할 필요가 있다.【F:infra-paper/src/main/java/kr/crownrpg/infra/paper/bootstrap/InfraBootstrap.java†L37-L134】

## 3️⃣ 현재 설계에서 매우 잘된 부분 (유지 권장)
* `infra-api` 계약이 환경·서버 가드 규칙을 명시하고 구현체가 이를 준수해 메시지 오염을 막는다.【F:infra-api/src/main/java/kr/crownrpg/infra/api/redis/RedisMessageRules.java†L7-L38】【F:infra-core/src/main/java/kr/crownrpg/infra/core/redis/LettuceRedisBus.java†L184-L205】
* RedisBus는 시작 전후 구독을 기억하고, 중복 핸들러를 억제하며, 예외로 버스가 죽지 않도록 보호한다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/redis/LettuceRedisBus.java†L37-L205】
* Netty 채널은 환경/토큰/peer 허용 목록을 모두 검증하는 HELLO/WELCOME 핸드셰이크를 거쳐 등록되지 않은 프레임을 차단한다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/HandshakeHandler.java†L16-L200】
* Paper 바인더가 Redis·DB·PubSub lifecycle을 일관되게 관리하고, ServiceRegistry 등록 실패 시에도 종료를 보장해 서버 전체 크래시를 막는다.【F:infra-paper/src/main/java/kr/crownrpg/infra/paper/bootstrap/InfraBootstrap.java†L37-L134】

## 4️⃣ 절대 바꾸지 말아야 할 설계 원칙
* `infra-api`와 구현(`infra-core`), 플랫폼 바인더(Paper/Velocity)의 분리 원칙 유지: 교체 가능성과 ClassLoader 충돌 회피의 핵심이다.【F:infra-paper/src/main/java/kr/crownrpg/infra/paper/bootstrap/InfraBootstrap.java†L37-L134】
* Redis/Netty 각각의 역할(비동기 브로드캐스트 vs 실시간 양방향)에 대한 분리와 환경/서버 가드 적용을 필수 규칙으로 유지한다.【F:infra-api/src/main/java/kr/crownrpg/infra/api/redis/RedisMessageRules.java†L7-L38】【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/HandshakeHandler.java†L16-L200】
* 모든 리소스에 start/stop 가드와 cleanup을 두어 이중 시작/종료나 누수를 방지하는 패턴을 깨지 않는다.【F:infra-core/src/main/java/kr/crownrpg/infra/core/redis/LettuceRedisBus.java†L60-L134】【F:infra-core/src/main/java/kr/crownrpg/infra/core/database/HikariDatabaseService.java†L23-L125】【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyRealtimeChannel.java†L60-L134】

## 5️⃣ 기능 플러그인 개발 전 권장 보완 작업 (우선순위)
1. **운영/디버깅 로그·메트릭 강화:** RedisBus 실행기 큐 포화, Netty outbound 드롭/재연결, DB 대기 시간을 로그·메트릭으로 노출하고 알림 연동.【F:infra-core/src/main/java/kr/crownrpg/infra/core/redis/LettuceRedisBus.java†L37-L205】【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyRealtimeChannel.java†L57-L150】【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyClient.java†L68-L161】
2. **Netty 인증/재연결 정책 보강:** 허용 peer 목록의 동적 갱신(예: Redis/DB 기반 allowlist)과 재연결 시도 횟수 상한·지수 백오프 설정을 외부화.【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyClient.java†L68-L161】【F:infra-core/src/main/java/kr/crownrpg/infra/core/realtime/NettyRealtimeChannel.java†L20-L74】
3. **RedisBus 개선:** 채널 구독 실패 재시도, 실행기 풀/큐 크기 설정값화, 대량 메시지 테스트 스위트 추가.【F:infra-core/src/main/java/kr/crownrpg/infra/core/redis/LettuceRedisBus.java†L37-L205】
4. **DB 접근 정책 문서화:** 트랜잭션 경계, 커넥션 사용 시간 상한, 플러그인별 스키마 네임스페이스 규칙 정의.【F:infra-core/src/main/java/kr/crownrpg/infra/core/database/HikariDatabaseService.java†L32-L137】
5. **공통 메시지 타입 표준화:** 기능 플러그인 간 Redis/Netty 메시지 payload 포맷을 명세화해 중복 정의와 역직렬화 오류를 예방.【F:infra-api/src/main/java/kr/crownrpg/infra/api/redis/RedisMessageRules.java†L7-L38】

## 6️⃣ 최종 결론
1. **실서비스 투입 가능성:** △ (핵심 가드와 핸드셰이크는 완비됐으나, 모니터링/동적 확장 부재로 장기 운영 시 관측 공백 위험)
2. **기능 플러그인 개발 착수:** 가능. 다만 위 우선순위 1~2 항목(모니터링·Netty 정책)을 병행 적용 추천.
3. **추천 로드맵 (1~5단계):**
   1) 운영 메트릭·로그/알림 추가, ServiceRegistry 의존 설정 명확화
   2) Netty 허용 peer·재연결 정책 외부화 및 부하 테스트
   3) RedisBus 구독 재시도·설정값화, 대량 메시지 테스트
   4) DB 트랜잭션/스키마 정책 문서화 후 기능 플러그인 착수
   5) 공통 메시지 타입/스키마 카탈로그 정리 및 플랫폼별 통합 리그레션 테스트
