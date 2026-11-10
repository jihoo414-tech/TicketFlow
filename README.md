# "TicketFlow"

여러 회원이 같은 좌석을 동시에 노려도 **한 좌석은 한 사람에게만** 돌아가도록 보장하는 선착순 공연 예매 서비스입니다.


---

## 📌 프로젝트 소개

예매 오픈 순간에는 많은 회원이 한꺼번에 몰려 같은 좌석을 동시에 선점하려 합니다. 이때 좌석이 중복으로 팔리지 않으면서, 정해진 서버 사양 안에서 최대한 많은 회원을 받아야 합니다.

**TicketFlow**는 이 과정을 하나의 서비스에서 관리합니다.

- 공연·회차·구역별 좌석 조회
- 같은 회차의 좌석 1~4석을 전부 선점하거나 전부 실패하는 선점
- 5분 안에 결제하면 예매 확정, 지나면 정리 작업 없이도 즉시 좌석 반환
- 내 예매 조회와 회차 시작 전 예매 취소
- 예매 오픈 피크를 받아 내는 대기열 서버와 입장권
- 이메일·비밀번호 가입과 JWT 로그인

개발 과정과 측정·개선 이야기는 [`docs/story.md`](docs/story.md)에 있습니다.

---
## 🛠 Tech Stack

### Backend

| | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.1.2 |
| ORM | Spring Data JPA |
| Security | Spring Security, OAuth2 Resource Server (JWT) |
| Database | MySQL 8.4 |
| Cache | Caffeine |
| Build | Gradle (Groovy) |

### Frontend

| | Technology |
| --- | --- |
| UI | React 19 |
| Build Tool | Vite |
| Language | TypeScript |
| Styling | Tailwind CSS 4 |
| Deployment | Spring Boot가 빌드 결과물을 서빙 |

### Test & Infra

| | Technology |
| --- | --- |
| Test | JUnit 5, Testcontainers (MySQL) |
| Load Test | k6, Python (`perf/run.py`, `perf/plot.py`) |
| Measurement | AWS EC2 t3.micro, RDS db.t4g.micro |
| Local DB | Docker Compose |

---
## Getting Started

### 준비물

- JDK 21
- Docker
- Node.js(npm) — Gradle이 프론트 빌드 때 호출합니다

### Backend
#### 1. 환경변수 설정

`.env.example`을 복사해 `.env`를 만듭니다.

```bash
cp .env.example .env
```

`.env`에서 MySQL 접속 정보와 `JWT_SECRET`을 설정합니다. `JWT_SECRET`은 32바이트 이상의 랜덤 값으로 설정하고, `local` 프로필에서는 비워 두면 로컬 전용 기본값을 사용합니다.

```env
MYSQL_HOST=localhost
MYSQL_PORT=3315
MYSQL_DATABASE=ticketflow
MYSQL_USER=ticketflow_user
MYSQL_PASSWORD=your_password
MYSQL_ROOT_PASSWORD=your_root_password
SPRING_PROFILES_ACTIVE=local
JWT_SECRET=your_jwt_secret
```

Spring은 `.env`를 직접 읽지 않습니다. IDE 실행 설정에 환경변수로 넣거나, 셸에서 export한 뒤 실행합니다.

#### 2. 실행 프로필 설정
`local` 프로필은 시작할 때 샘플 데이터를 넣습니다.

- 회원 3명: `member1@ticketflow.local` ~ `member3@ticketflow.local`, 비밀번호는 모두 `password1234`
- 레미제라블 2회차(하나는 예매 오픈 전, 3분 뒤 오픈), 오페라의 유령 1회차
- 회차마다 1~5열 × 10석, A구역(1~5번)과 B구역(6~10번)

#### 3. DB 스키마 관리
DB 테이블은 시작할 때 `schema.sql`로 만듭니다.

> ⚠️ 기본 설정은 시작할 때마다 **모든 테이블을 지우고 다시 만듭니다**. 로컬 데이터는 재시작하면 사라집니다.

#### 4. MySQL 실행

```bash
docker compose up -d
```

#### 5. 애플리케이션 실행

```bash
./gradlew bootRun
```

`bootRun`은 프론트를 먼저 빌드(`frontend` → `src/main/resources/static`)한 뒤 서버를 띄웁니다. 브라우저에서 [http://localhost:8080](http://localhost:8080)을 엽니다.

#### 6. 대기열 서버 실행

구역·좌석 조회와 선점은 대기열 서버가 발급한 입장권이 있어야 합니다. 대기열 서버는 `8081` 포트에서 실행됩니다.

```bash
./gradlew :queue:bootRun --args='--spring.profiles.active=local'
```

### Frontend

프론트만 따로 개발할 때 사용합니다.

#### 1. 의존성 설치

```bash
cd frontend
npm install
```

#### 2. 개발 서버 실행

```bash
npm run dev
```

Vite 개발 서버가 대기열 요청은 `localhost:8081`로, 나머지 `/api` 요청은 `localhost:8080`으로 넘깁니다. 백엔드와 대기열 서버는 따로 실행해 둡니다.

브라우저에서 [http://localhost:5173](http://localhost:5173)을 엽니다.

### Test

```bash
./gradlew test
```

Testcontainers가 MySQL 컨테이너를 띄우므로 Docker가 켜져 있어야 합니다.

---
