# 회원은 Spring Security가 검증한 JWT로 식별한다

`X-Member-Id` 헤더는 아무 회원 id나 넣으면 그 회원이 되므로 서비스를 공개할 수 없다. 로그인하면 서버가 서명한 JWT를 주고, 회원이 필요한 요청은 `Authorization: Bearer` 헤더로 이 토큰을 보낸다. 서버는 세션을 저장하지 않고 서명만 검증한다.

- **토큰**: HS256으로 서명한 액세스 토큰 하나. `sub`는 회원 id, 만료는 1시간. 재발급 토큰과 블랙리스트는 두지 않는다. 서명 키는 환경 변수 `JWT_SECRET`으로 주입한다.
- **검증**: Spring Security와 `spring-boot-starter-oauth2-resource-server`가 토큰을 검증한다. 변환기가 `sub`를 회원 id(`Long`)로 바꿔 principal에 넣고, 컨트롤러는 `@AuthenticationPrincipal Long memberId`로 받는다. `@CurrentMemberId`, `CurrentMemberIdArgumentResolver`, 이를 등록하던 `WebConfig`는 지운다.
- **회원 존재 확인**: 요청 앞단에서 하지 않는다. 토큰은 로그인에 성공해야 받고 회원 삭제 기능이 없으므로, 토큰의 회원 id는 존재하는 회원이다. 회원이 꼭 있어야 하는 작업은 서비스가 회원을 읽으며 `MEMBER_NOT_FOUND`를 낸다(선점의 회원 락, `/me` 조회). `checkExists` 옵션은 없앤다.
- **가입·로그인**: 이메일(UNIQUE)과 비밀번호(8자 이상, BCrypt 해시)로 가입하고 로그인한다. 가입·로그인 화면을 만들고, 회원 고르기 화면과 `GET /api/members`는 지운다. 로그아웃은 프론트가 토큰을 지운다. 이메일 인증과 비밀번호 찾기는 하지 않는다.
- **토큰 보관**: 프론트는 토큰을 `localStorage`에 둔다. 탭을 여러 개 띄워도 다시 로그인하지 않는다.
- **부하 테스트**: k6가 측정 전용 `JWT_SECRET`으로 회원마다 토큰을 직접 서명한다. 측정에는 로그인(BCrypt) 비용 없이 토큰 검증 비용만 들어간다. 예매 오픈 전에 미리 로그인해 둔 회원이 몰리는 상황과 같다.

| 상황 | HTTP | code |
|---|---|---|
| 토큰이 없다 | 401 | `AUTH_TOKEN_MISSING` |
| 서명이 틀리거나 형식이 깨졌다 | 401 | `AUTH_TOKEN_INVALID` |
| 만료됐다 | 401 | `AUTH_TOKEN_EXPIRED` |
| 이메일 또는 비밀번호가 틀렸다(같은 메시지) | 401 | `LOGIN_FAILED` |
| 이미 가입한 이메일이다 | 409 | `EMAIL_ALREADY_EXISTS` |

## Considered Options

- **세션**: 로그아웃과 강제 만료가 쉽다. 하지만 부하 테스트는 실행마다 앱을 재시작하므로 메모리 세션이 사라지고, 매번 측정 인원만큼 BCrypt 로그인을 다시 해야 한다. 회원마다 서버 메모리도 차지한다. 서버를 여러 대로 늘리면 공유 저장소가 필요하다.
- **HttpOnly + `SameSite=Strict` 쿠키로 토큰 전달**: 화면에 XSS가 생겨도 토큰을 훔칠 수 없다. 하지만 더 흔하고 익숙한 Bearer 헤더를 골랐다. 대가로 XSS가 생기면 토큰이 새어 나갈 수 있다. React의 기본 이스케이프에 기대고 `dangerouslySetInnerHTML`을 쓰지 않는다.
- **Spring Security 없이 Resolver에서 직접 검증**: 가장 가볍다. 하지만 검증 코드를 직접 짜야 하고, 접근 규칙을 URL 기준으로 한곳에 모을 수 없다.
- **`@CurrentMemberId`를 `@AuthenticationPrincipal`의 메타 애너테이션으로 유지**: 컨트롤러가 덜 바뀐다. 하지만 표준 애너테이션을 그대로 쓰는 편이 읽는 사람에게 익숙하다.
- **테스트용 토큰 발급 API**: 가장 쉽지만, 누구나 아무 회원의 토큰을 받는 구멍이 된다.

## Consequences

- 이 ADR은 ADR 0007(헤더와 ArgumentResolver로 식별)을 대체한다.
- 토큰이 평문 HTTP로 오가면 가로챌 수 있다. 외부에 공개하기 전에 HTTPS를 먼저 둔다.
- 도입 전후를 AWS에서 같은 방식으로 측정해 Security의 요청당 비용을 기록한다.
