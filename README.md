# TicketFlow

선착순 공연 예매 서비스. 여러 회원이 같은 좌석을 동시에 노려도 **한 좌석은 한 사람에게만** 돌아가야 한다.

회원은 회차의 좌석 1~4석을 5분 동안 선점하고, 그 안에 결제하면 예매가 확정된다. 결제하지 않으면 선점은 만료되고 좌석은 다시 열린다.

## MVP 범위

| 기능 | 내용 |
|---|---|
| 공연·회차·좌석 조회 | 공연 목록, 공연의 회차 목록, 회차의 좌석 상태(빈 좌석 / 점유됨) |
| 선점 | 같은 회차의 좌석 1~4석을 전부 선점하거나 전부 실패한다. 예매 오픈 시각 전에는 선점할 수 없다. |
| 선점 취소 | 결제 전에 자기 선점을 포기하고 좌석을 연다. |
| 선점 만료 | 5분이 지나면 정리 작업이 없어도 그 즉시 좌석이 다시 열린다. |
| 결제 → 예매 | 만료 전에 시작된 결제만 예매가 된다. 결제 진행 중인 선점은 만료되지 않는다. |
| 예매 조회·취소 | 내 예매 목록(공연·회차·좌석·금액·상태)을 보고, 회차 시작 전에 예매 전체를 취소한다. |
| 화면 | React 화면에서 회원을 고르고 위 흐름을 모두 진행할 수 있다. |

회원 인증은 아직 없다. Security 도입 전까지 회원은 `X-Member-Id` 헤더로 식별한다.

## 핵심 규칙

어떤 순서와 동시성으로 요청이 와도 지켜야 하는 규칙은 [`docs/invariants.md`](docs/invariants.md)에 있다. 요약하면:

- 한 좌석은 어느 순간에도 유효한 선점 또는 예매 **최대 하나**에만 속한다.
- 한 회원은 한 회차에 유효한 선점을 **하나만** 가지고, 선점과 예매를 합쳐 **4석 이하**다.
- 하나의 선점에서는 예매가 최대 하나 생긴다.

### 동시성 제어

좌석 점유를 바꾸는 모든 작업(선점, 선점 취소, 결제, 예매 취소)은 `SeatOccupancyLock` 한 곳에서 락을 잡는다.

1. 회원 행을 `SELECT … FOR UPDATE`로 잠근다. (회원별 제한: 회차당 선점 하나, 4석)
2. 대상 좌석 행들을 **id 오름차순**으로 잠근다. (데드락 방지)
3. `READ COMMITTED`로 최신 커밋 값을 다시 확인한 뒤 쓴다.

락 순서는 항상 회원 → 좌석이다. 자세한 근거는 [ADR 0008](docs/adr/0008-serialize-seat-occupancy-with-row-locks.md), [ADR 0011](docs/adr/0011-acquire-occupancy-locks-in-one-place.md), 경쟁 상황 분석은 [`docs/concurrency-cases.md`](docs/concurrency-cases.md)를 본다.

## 기술 스택

- **백엔드**: Java 21, Spring Boot 4, Spring Data JPA, MySQL 8.4, Gradle(Groovy)
- **프론트엔드**: React 19, Vite, TypeScript, Tailwind CSS — 빌드 결과물을 Spring이 서빙한다
- **테스트**: JUnit 5, Testcontainers(MySQL). H2와 Mock은 쓰지 않는다.
- **부하 테스트**: k6

## 실행하기

### 준비물

- JDK 21
- Docker
- Node.js(npm) — Gradle이 프론트 빌드 때 호출한다

### 1. 환경 변수

`.env.example`을 복사해 `.env`를 만들고 값을 채운다.

```bash
cp .env.example .env
```

| 변수 | 설명 |
|---|---|
| `MYSQL_HOST` | 보통 `localhost` |
| `MYSQL_PORT` | 호스트 포트. 비우면 docker-compose는 `3315`를 쓴다 |
| `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | DB 접속 정보 |
| `SPRING_PROFILES_ACTIVE` | `local`이면 시작할 때 샘플 데이터를 넣는다 |

Spring은 `.env`를 직접 읽지 않는다. IDE 실행 설정에 환경 변수로 넣거나, 셸에서 export한 뒤 실행한다.

### 2. MySQL 실행

```bash
docker compose up -d
```

### 3. 서버 실행

```bash
./gradlew bootRun
```

`bootRun`은 프론트를 먼저 빌드(`frontend` → `src/main/resources/static`)한 뒤 서버를 띄운다. http://localhost:8080 으로 접속한다.

> ⚠️ 기본 설정은 시작할 때마다 `schema.sql`로 **모든 테이블을 지우고 다시 만든다**. 로컬 데이터는 재시작하면 사라진다.

`local` 프로파일의 샘플 데이터:

- 회원 3명 (회원1~3)
- 레미제라블 2회차(하나는 예매 오픈 전, 3분 뒤 오픈), 오페라의 유령 1회차
- 회차마다 A~E열 × 10석

### 프론트만 따로 개발할 때

```bash
cd frontend
npm install
npm run dev
```

Vite 개발 서버가 `/api` 요청을 `localhost:8080`으로 넘긴다. 백엔드는 따로 실행해 둔다.

## 테스트

```bash
./gradlew test
```

Testcontainers가 MySQL 컨테이너를 띄우므로 Docker가 켜져 있어야 한다. 테스트는 세 종류다([ADR 0005](docs/adr/0005-test-strategy.md)).

| 종류 | 위치 | 검증하는 것 |
|---|---|---|
| 기능 | `src/test/.../domain/**/*ApiTest` | HTTP 요청과 응답으로 동작 하나 |
| 동시성 | `src/test/.../concurrency` | 여러 스레드가 동시에 요청해도 불변식이 지켜지는지 |
| 단위 | `src/test/.../entity` | 엔티티의 규칙 |

시각은 주입된 `Clock`에서 읽으므로, 테스트는 시계를 움직여 만료·오픈 시각을 재현한다([ADR 0004](docs/adr/0004-datetime-with-injected-clock.md)).

## API

성공하면 DTO를 그대로, 실패하면 RFC 9457 `ProblemDetail`에 `code`(`ErrorCode` 이름)를 더해 응답한다([ADR 0006](docs/adr/0006-response-format.md)). 회원이 필요한 요청은 `X-Member-Id` 헤더를 보낸다.

| 메서드 | 경로 | 설명 | 회원 |
|---|---|---|:---:|
| GET | `/api/members` | 회원 목록 | |
| GET | `/api/members/me` | 현재 회원 | ✓ |
| GET | `/api/performances` | 공연 목록 | |
| GET | `/api/performances/{performanceId}/showtimes` | 공연의 회차 목록 | |
| GET | `/api/showtimes/{showtimeId}/seats` | 회차의 좌석 상태 | |
| POST | `/api/showtimes/{showtimeId}/holds` | 선점 (`{"seatIds": [1, 2]}`) | ✓ |
| POST | `/api/holds/{holdId}/cancellation` | 선점 취소 | ✓ |
| POST | `/api/holds/{holdId}/payment` | 결제 → 예매 | ✓ |
| GET | `/api/members/me/reservations` | 내 예매 목록 | ✓ |
| POST | `/api/reservations/{reservationId}/cancellation` | 예매 취소 | ✓ |

오류 코드 전체는 `global/exception/ErrorCode.java`에 있다.

## 프로젝트 구조

패키지는 "누가 이 규칙을 소유하는가"로 나누고, 그 안은 controller / service / repository 3레이어로 고정한다([ADR 0001](docs/adr/0001-domain-package-structure.md)).

```
src/main/java/com/ticketflow
├── domain
│   ├── member        회원, X-Member-Id 해석
│   ├── performance   공연·회차·좌석
│   └── booking       선점·결제·예매, 좌석 점유 락
└── global            설정, 예외 처리, 로컬 초기 데이터
frontend/             React + Vite 화면
perf/                 k6 부하 테스트 스크립트와 측정 기록
docs/                 불변식, 동시성 케이스, ADR
```

## 성능 측정

`perf/`에 좌석 조회 부하 테스트(k6)와 측정 기록이 있다. 6,000석 회차에 만료된 선점 이력이 쌓일 때의 조회 지연을 보고, 점유 이력을 DB에서 거르고([ADR 0013](docs/adr/0013-filter-occupied-seats-in-database.md)) 복합 인덱스를 추가했다([ADR 0014](docs/adr/0014-index-occupied-seat-lookup.md)). 실험 데이터는 `perf` 프로파일로 별도 DB에서 다룬다(시작할 때 스키마를 초기화하지 않음).

```bash
k6 run -e SHOWTIME_ID=1 -e BASE_URL=http://localhost:8080 perf/seat-load-test.js
```

## 문서

| 문서 | 내용 |
|---|---|
| [`GLOSSARY.md`](GLOSSARY.md) | 용어(공연, 회차, 좌석, 선점, 예매 …)와 피해야 할 말 |
| [`docs/invariants.md`](docs/invariants.md) | 항상 참이어야 하는 규칙 |
| [`docs/concurrency-cases.md`](docs/concurrency-cases.md) | 동시성 경쟁 상황과 해결 방식 검토 |
| [`docs/adr/`](docs/adr/) | 설계 결정 기록 |
| GitHub 이슈 #1 | 요구사항 스펙과 하위 티켓 |
