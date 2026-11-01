# 요청한 회원은 `@CurrentMemberId` + ArgumentResolver 한 곳에서 식별한다

Security를 도입하기 전까지 회원은 `X-Member-Id` 헤더로 식별한다. 이 헤더를 읽는 곳은 `CurrentMemberIdArgumentResolver` 하나다. 이 Resolver는 헤더 값을 숫자로 바꾸고 회원이 존재하는지 확인한 뒤, `@CurrentMemberId Long memberId` 파라미터에 넣는다. 컨트롤러는 헤더를 모르고, 검증이 끝난 회원 id만 받는다. 어노테이션과 Resolver는 `domain/member/controller`에 두고, Resolver 등록은 `global/config/WebConfig`에서 한다.

회원을 식별하지 못하면 모두 401로 응답하고, 원인은 `code`로 나눈다.

**예외: 선점 API는 앞단의 존재 확인을 건너뛴다**(`@CurrentMemberId(checkExists = false)`). 선점은 트랜잭션 안에서 회원 행을 `FOR UPDATE`로 잠그고, 없으면 같은 `MEMBER_NOT_FOUND`를 낸다. 앞단 확인은 트랜잭션 밖이라 커넥션을 한 번 더 빌리는데, 예매 오픈 피크에 가장 많이 몰리는 요청이 선점이라 이 비용을 없앤다. 요청 본문이 잘못되고 회원도 없으면, 선점 API는 본문 오류가 먼저 나간다.

| 상황 | code |
|---|---|
| 헤더가 없거나 비어 있다 | `MEMBER_ID_MISSING` |
| 헤더 값이 숫자가 아니다 | `MEMBER_ID_INVALID` |
| 그 id의 회원이 없다 | `MEMBER_NOT_FOUND` |

## Considered Options

- **인터셉터**: 모든 요청 앞에서 검사하는 모양이 직관적이다. 하지만 검사(인터셉터)와 꺼내 쓰기(`@RequestAttribute`)가 두 곳으로 나뉘고, 적용할 URL 목록을 따로 관리해야 한다.
- **컨트롤러마다 `@RequestHeader("X-Member-Id")`**: 새 파일이 없다. 하지만 헤더 이름과 회원 확인이 API마다 반복되어 "한 곳에서 식별한다"를 지키지 못한다.
- **전부 `global/config`에 두기**: ADR 0001을 바꾸지 않아도 된다. 하지만 Resolver는 설정이 아니라 동작하는 코드라 폴더 이름과 맞지 않는다.
- **새 폴더 `global/web`**: 교체할 곳이 가장 분명하다. 하지만 ADR 0001의 "폴더를 늘리지 않는다"와 부딪힌다.
- **400 / 400 / 404로 응답**: HTTP 의미에는 맞다. 하지만 선점 API에서 404가 나면 좌석이 없는지 회원이 없는지 상태만으로 구분할 수 없다.

## Consequences

- `booking` 컨트롤러는 member 패키지의 `@CurrentMemberId`를 import한다.
- 회원 목록 조회(`GET /api/members`)는 회원을 고르기 위한 API라서 헤더를 요구하지 않는다.
- 헤더가 없어도 동작해야 하는 API(예: 내 선점을 표시하는 좌석 조회)가 생기면 이 규칙을 다시 판단한다.
