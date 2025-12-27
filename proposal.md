# Crown-Infra 최종 기획서 (FINAL)

**프로젝트명:** Crown-Infra
**목적:** CrownRPG 시즌2 멀티서버( Velocity + Paper ) 환경에서 공통 인프라( Redis / MySQL / 메시징 )를 표준화하고, 모든 기능 플러그인이 “안정적으로” 의존할 수 있는 기반 레이어를 제공한다.
**대상 버전:** Java 21, Paper 1.21.8, Velocity 최신 안정(운영 시점 기준), MySQL 8.x, Redis 6/7 권장, Lettuce + HikariCP 사용.

---

## 1. 개요

Crown-Infra는 “게임 기능”을 제공하지 않는다.
대신, CrownRPG 서버군에서 제작될 모든 기능 플러그인(도감/우편/스탯/스킬/경제/퀘스트/채팅 등)이 공통으로 사용하는 다음 기반 기능을 제공한다.

1. **Redis 기반 메시지 버스(PubSub)**
2. **MySQL 접근 표준(커넥션 풀 + 트랜잭션 실행)**
3. **서버 식별/환경 구분(Context)**
4. **플랫폼 바인딩(Paper/Velocity에서 동일 계약으로 동일 동작)**
5. **옵션형 Realtime 채널 계약(향후 Netty 기반 통신 확장 대비 – 구현은 후순위)**
6. **CrownLib(ServiceRegistry 등)와의 연결(Binder) 규칙 확립**

> 핵심 원칙: “계약과 구현을 분리하고, 운영 안정성을 최우선한다.”

---

## 2. 목표 / 비목표

### 2.1 목표 (Goals)

* 멀티 서버 환경에서 **표준 메시지 규약**으로 Paper ↔ Velocity ↔ Paper 간 이벤트/알림/요청을 송수신 가능
* Redis, MySQL 연결을 **안정적으로 관리**하고 장애 시 원인을 추적 가능하게 구성
* 기능 플러그인이 인프라 라이브러리(Lettuce/Hikari/JDBC)에 직접 의존하지 않도록 **추상 계약 제공**
* CrownLib을 기반으로 각 서비스(버스/DB 등)를 **일관되게 등록/조회**할 수 있도록 통합
* 시즌2 운영 중 서버 증설/교체/재시작에도 구조가 흔들리지 않는 안정성 확보
* 향후 “진짜 초저지연이 필요한 기능”이 생길 경우를 대비해 **Realtime(Netty) 계약을 미리 확보**

### 2.2 비목표 (Non-goals)

* 인게임 기능(스킬/퀘스트/경제/채팅)을 구현하지 않음
* Redis Streams/Kafka 같은 메시지 시스템으로 확장하지 않음(필요 시 별도 프로젝트)
* Realtime(Netty) 구현은 본 문서에서 **계약까지만** 정의하며 기본 릴리즈 범위에는 포함하지 않음
* DB 마이그레이션 툴(Flyway 등) 내장하지 않음(필요 시 별도 도구/프로젝트)

---

## 3. 시스템 아키텍처

### 3.1 모듈 구조 (멀티모듈)

* **infra-api**: 순수 계약(Contract). 외부 라이브러리/플랫폼 비의존.
* **infra-core**: 실제 구현(Implementation). Lettuce/Hikari/Jackson 등 의존 허용.
* **infra-paper**: Paper 플러그인 바인딩. config.yml 로드 + core를 초기화/종료 + CrownLib 등록.
* **infra-velocity**: Velocity 플러그인 바인딩. TOML/JSON 설정 로드 + core 초기화/종료 + (옵션) Velocity 서비스 등록.

> 코드 공유는 infra-api + infra-core를 통해 이루어지며, 플랫폼별 차이는 바인딩 레이어에서만 존재한다.

---

## 4. 공통 개념 정의

### 4.1 Environment

* 운영 환경 식별자. 예: `prod`, `dev`, `stage`, `local`
* Redis 채널 prefix 및 메시지 필터링에 사용
* 서로 다른 환경이 섞여 통신하는 사고를 원천 차단

### 4.2 ServerId

* 각 서버(Proxy 포함)를 구분하는 고유 문자열
* 예: `proxy-1`, `village-1`, `dungeon-2`, `wild-1`
* self-loop 방지, 발신자 추적, 디버깅의 핵심 키

---

## 5. 기능 명세

## 5.1 Redis PubSub Bus (표준 메시지 버스)

### 5.1.1 목적

* Paper ↔ Velocity ↔ Paper 간 “표준 메시지”를 송수신한다.
* 채팅/공지/우편 알림/경제 동기화/길드·파티 이벤트/도감 갱신 등 다양한 플러그인들이 공통 버스로 사용한다.

### 5.1.2 메시지 계약(InfraMessage)

모든 메시지는 표준 Envelope를 사용한다.

* `environment`: 대상 환경
* `fromServerId`: 발신 서버
* `type`: 메시지 타입 (예: `chat.broadcast`, `notice.global`, `economy.balance_sync`)
* `payload`: 문자열 (권장: JSON 문자열)
* `payloadFormat`: `"json"` 또는 `"text"`
* `meta`: messageId/createdAt/headers 포함 (불변)

#### 운영 규칙

* environment가 다르면 수신하더라도 무시한다.
* fromServerId가 자기 자신이면 self-loop로 간주하여 무시(기본 정책).
* payload는 infra-api에서 JSON 라이브러리 비의존을 위해 문자열로만 취급한다.
* 메시지 타입은 기능 플러그인별 네임스페이스를 권장한다.
  예: `mail.reward_grant`, `party.member_join`, `guild.created`, `chat.channel_message`

### 5.1.3 채널 규약

* 채널명은 문자열이며, prefix로 환경을 포함한다.
* 권장 예시:

    * `crown:{env}:global`
    * `crown:{env}:proxy`
    * `crown:{env}:paper`
    * `crown:{env}:chat`
    * `crown:{env}:economy`

> 실제 채널 전략은 “전역 1채널 + type으로 라우팅”을 기본으로 하며, 필요 시 “도메인별 채널”을 추가한다.

### 5.1.4 동작 흐름

1. 플러그인(채팅/공지/경제 등)이 Infra에 publish 요청
2. infra-core의 RedisBus가 문자열로 직렬화하여 Redis publish
3. 구독 중인 다른 서버가 수신 → 역직렬화 → handler 호출
4. handler는 type/environment/fromServerId를 검사하여 처리

### 5.1.5 신뢰성 정책(기본)

* PubSub은 기본적으로 “전달 보장”이 없다(브로커 기반 실시간).
* **중요 데이터는 DB를 소스 오브 트루스로 유지**하고 PubSub은 “이벤트 트리거”로 사용한다.
* 유실 허용 불가 이벤트는 별도 보강(ACK/재시도/Redis Stream/DB polling 등)을 후속 설계로 둔다.

---

## 5.2 Database (MySQL/Hikari) 표준화

### 5.2.1 목적

* 다중 플러그인이 공통 DB에 접근할 때 연결/트랜잭션/자원 누수를 표준화한다.
* 기능 플러그인은 JDBC/Hikari를 직접 다루지 않도록 한다.

### 5.2.2 계약(Infra Database API)

* `DatabaseService`

    * `execute(TransactionCallback<T>)`
    * `executeVoid(TransactionVoidCallback)`
    * `close()`
* `DatabaseConfig`

    * host/port/database/username/password/poolSize/parameters 등

### 5.2.3 구현(infra-core)

* HikariCP로 커넥션 풀 구성
* 트랜잭션 헬퍼 제공:

    * 성공 시 commit
    * 예외 시 rollback
    * connection/statement/resultSet 자동 정리

### 5.2.4 운영 규칙

* “상태(돈/우편/길드/파티/도감 진행도)”는 DB가 최종 진실이다.
* PubSub은 “변경됨”을 알리는 트리거로 사용하고, 각 서버는 필요 시 DB 재조회/캐시 갱신.

---

## 5.3 CrownLib 연동(Binder/Registry)

### 5.3.1 목적

* 기능 플러그인들이 `CrownLib`의 ServiceRegistry를 통해

    * RedisBus
    * DatabaseService
    * (향후) RealtimeChannel
    * InfraContext
      를 통일된 방식으로 조회 가능하게 한다.

### 5.3.2 등록 정책

* CrownLib의 ServiceRegistry는 전역 특성이 있으므로 덮어쓰기 위험이 있다.
* 따라서 CrownInfra는 등록 시:

    * “중복 등록 방지 메서드(registerOnce)”를 사용하거나
    * 최소한 자체 검증 후 등록한다.

### 5.3.3 기능 플러그인에서의 사용 방식(표준)

* onEnable 시 ServiceRegistry에서 필요한 서비스 조회
* 없으면 “CrownInfra 미설치/비활성”로 판단하고 기능 제한 또는 disable

---

## 5.4 RealtimeChannel (Netty) — 옵션형 계약만 제공

### 5.4.1 목적

* 향후 “진짜 초저지연/1:1 요청-응답”이 필요한 기능이 생길 수 있다.
* 지금 당장은 구현하지 않지만, infra-api에 계약을 마련해 설계 흔들림을 방지한다.

### 5.4.2 범위

* infra-api: `RealtimeChannel` 인터페이스(계약)만 포함
* infra-core/paper/velocity: 기본 릴리즈에서는 **구현하지 않음**
* 향후 별도 모듈(예: `infra-realtime`)로 Netty 구현을 추가 가능

### 5.4.3 왜 채팅/공지는 Redis가 기본인가

* 채팅/공지의 핵심 요구는 fan-out/운영 안정성/확장성이다.
* Redis PubSub은 지연도 충분히 낮고, 다중 서버 구조에 매우 유리하다.
* Netty를 기본으로 채택하면 연결관리/장애전파/운영 복잡도가 급증한다.

---

## 6. 설정(Config) 표준

### 6.1 Paper (infra-paper/config.yml)

* environment
* server-id
* redis: host/port/password/ssl/channelPrefix 등
* database: host/port/database/user/pass/poolSize/params
* logging: debug, messageLog 등

### 6.2 Velocity (infra-velocity/config.toml or config.yml)

* 동일 키 구조 권장
* environment, server-id 반드시 존재

### 6.3 필수 검증

* environment, server-id는 공백 불가
* Redis host/port 필수
* DB host/database/user 필수(사용 설정일 때)

---

## 7. 생명주기(Lifecycle) & 장애 처리

### 7.1 초기화 순서(권장)

1. 설정 로드 및 검증
2. DatabaseService 초기화(Hikari)
3. RedisBus 초기화(Lettuce)
4. RedisBus start + subscribe 등록
5. CrownLib ServiceRegistry 등록
6. “Boot OK” 로그 출력

### 7.2 종료 순서

1. 서비스 등록 해제(선택)
2. RedisBus close
3. DatabaseService close
4. “Stopped” 로그 출력

### 7.3 장애 정책

* 부팅 시 Redis/DB 연결 실패:

    * 운영 모드에서 CrownInfra는 **disable**이 기본 정책(안전 우선)
* 실행 중 연결 끊김:

    * core에서 재연결 정책을 제공(추후 옵션)
    * 최소한 에러 로그 + 서비스 상태 플래그 제공

---

## 8. 스레드/실행 컨텍스트 규칙

* infra-api는 스레드를 모른다.
* infra-core는 Redis 수신 스레드를 가질 수 있다(Lettuce 내부).
* 플랫폼 바인더(infra-paper/infra-velocity)는 다음 규칙을 지킨다:

    * Paper 메인 스레드에서 처리해야 하는 작업은 스케줄러로 넘긴다.
    * Velocity도 동일하게 메인 이벤트 루프 규칙을 준수한다.
* 메시지 핸들러는 기본적으로 “수신 스레드에서 호출될 수 있음”을 전제로 설계한다.

    * 따라서 기능 플러그인은 thread-safe 하거나 플랫폼 스레드로 디스패치해야 한다.

---

## 9. 제공 API 요약 (기능 플러그인 관점)

기능 플러그인이 사용하게 될 서비스:

* `InfraContext` (env, serverId)
* `RedisBus`

    * `publish(channel, InfraMessage)`
    * `subscribe(channel, handler)`
* `DatabaseService`

    * `execute(...)`, `executeVoid(...)`
* `RealtimeChannel` (계약만, 구현 없음)

---

## 10. 예시 사용 시나리오

### 10.1 채팅 브로드캐스트

* Paper 서버에서 플레이어 채팅 발생
* chat 플러그인이 `InfraMessage(type="chat.broadcast")` 발행
* 모든 서버(다른 Paper + Velocity)가 수신
* 각 서버는 자기 서버의 플레이어에게 채팅 전송

### 10.2 공지(Global Notice)

* 관리자 명령 실행(어느 서버든)
* notice 플러그인이 `notice.global` 발행
* 모든 서버가 수신 후 타이틀/채팅/액션바로 공지 노출

### 10.3 경제 동기화

* 특정 서버에서 돈 변경 이벤트 발생
* `economy.balance_changed` 발행
* 다른 서버는 해당 유저의 캐시를 무효화하고 DB 재조회

---

## 11. 품질 기준 / 완료 조건(Definition of Done)

### 11.1 빌드/배포

* 멀티모듈 Gradle 구성
* JitPack 또는 사내 Maven으로 의존 가능
* Paper/Velocity 각각 plugin artifact 생성 가능

### 11.2 기능 테스트

* Paper ↔ Velocity 양방향 publish/subscribe 성공
* fromServerId self-loop 필터 동작
* environment mismatch 필터 동작
* DB 트랜잭션 commit/rollback 검증
* 서버 재시작 시 자원 누수 없이 정상 종료

### 11.3 운영 로그

* Boot OK/FAILED 로그
* Redis 연결 정보(민감정보 제외)
* DB 연결 정보(민감정보 제외)
* 메시지 디버그 옵션(샘플링/타입 필터링)

---

## 12. 결론

Crown-Infra는 CrownRPG 시즌2의 모든 기능 플러그인이 의존할 “기반 인프라 레이어”이며,
Redis PubSub을 표준 통신으로 사용하고, MySQL을 최종 진실 소스로 유지하며,
CrownLib과의 결합은 “서비스 등록/조회” 수준에서만 수행한다.

또한 향후 초저지연 실시간 요구가 생길 경우를 대비해 Realtime(Netty) 채널은 계약만 미리 확보하되,
현재 릴리즈 범위에는 포함하지 않고 Redis 기반 구조를 우선 완성한다.

---