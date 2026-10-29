# Session Log

아직 기록된 세션이 없습니다. 각 세션에 목표, 사용자와 AI의 구현 범위, 검증한 개념, 테스트 결과, 지식 공백, 다음 과제와 레벨 조정 제안을 기록합니다.

## 2026-10-29 — 선점 묶음에 다른 선점의 좌석이 있으면 실패

- 목표: 다른 선점이 이미 잡은 좌석이 묶음에 하나라도 있으면 409 `SEAT_ALREADY_HELD`로 실패한다 (만료·취소 상태 검사는 다음 테스트로 미룸)
- 사용자 구현 범위: 설계 결정 (HoldService가 HoldSeatRepository를 직접 사용, `exists` 계열 조회), HoldSeat 생성 스트림과 빈 HoldSeatRepository 작성을 시작함
- AI 구현 범위: RED 테스트 작성. 사용자가 중간에 GREEN 구현을 위임해 `existsBySeatIdIn`, `SEAT_ALREADY_HELD`, `validateSeatsNotHeld`, `saveHoldSeats`를 구현함
- 검증한 개념: HoldSeat을 Hold 묶음의 일부로 보고 repository를 직접 쓰는 기준을 판단함. `exists` 조회가 존재 여부만 묻는 질문에 맞는다는 점을 설명함
- 테스트 결과: RED는 409 기대, 201 응답으로 의도한 이유로 실패함을 확인. GREEN 후 전체 18개 통과
- 지식 공백: 요청 seat id로 HoldSeat을 조회하는 관점 전환 (연관 엔티티 id로 거르는 방식), Spring Data 파생 쿼리 이름 규칙 (`SeatIdIn`). 사용자가 직접 확인하지 않고 위임함
- 다음 과제: 만료·취소된 선점의 좌석은 다시 선점할 수 있다. 확인 후 저장 방식의 동시성 공백 (ADR 0008 행 락)
- 레벨 조정 제안: 없음. 선점 겹침 검증 영역은 L0로 시작했으나 구현은 위임됨. 근거가 부족해 미검증으로 둠
- 사후 점검: `saveHoldSeats` 제거 시 마지막 테스트가 201을 받아 실패함을 사용자가 정확히 설명함. 검증을 `saveHold` 뒤로 옮기는 경우 처음에 "불일치 발생"이라 답했으나, 질문을 받고 `@Transactional` 롤백으로 남지 않음을 스스로 찾음. 검증을 `saveHoldSeats` 뒤로 옮기면 같은 트랜잭션에서 자기 HoldSeat이 보여 모든 선점이 409가 되는 점은 H5로 답을 보고 이해함 (직접 도출 아님)

## 2026-10-29 — 예매 오픈 시각 전 선점 거절

- 목표: 예매 오픈 시각 전에는 선점을 만들 수 없다 (정각은 허용)
- 사용자 결정: seam은 엔티티 단위 테스트만 사용 (API 기능 테스트 생략). 판단은 `Hold.create`가 `Showtime` 메서드를 호출하고, 예외는 `Showtime`이 던진다
- AI 구현 범위: `HoldTest` 두 개(임시 이름), `ErrorCode.BOOKING_NOT_OPEN`(400), `Showtime.validateBookingOpen`, `Hold.create`에서 호출
- 테스트 결과: RED는 1초 전 테스트가 "예외가 나지 않음"으로 실패함을 확인. 정각 테스트는 처음부터 통과(RED 없음). GREEN 후 전체 20개 통과
- 지식 공백: 미확인 (이번 슬라이스는 설명 점검을 하지 않음)
- 다음 과제: 테스트 이름을 사용자가 직접 지정. 0석·5석 이상 거절. API 기능 테스트를 생략한 결정을 ADR 0005와 맞출지 판단
- 레벨 조정 제안: 없음

## 2026-10-29 — 0석·5석 이상 선점 거절

- 목표: 선점 좌석이 1~4석이 아니면 INVALID_SEAT_COUNT(400)로 거절
- 사용자 결정: 규칙은 `Hold.create`에 좌석을 넘겨 검증 (엔티티 단위 테스트). 개수는 조회된 seats 기준
- AI 구현 범위: `Hold.create` 시그니처에 seats 추가, `validateSeatCount`, `HoldTest`에 0·4·5석 테스트(임시 이름)
- 검증한 개념: 사용자의 처음 제안 "0 또는 5"가 6석 이상을 놓친다는 점을 AI가 지적함
- 테스트 결과: RED는 0석·5석 테스트가 "예외가 나지 않음"으로 실패함을 확인. GREEN 후 전체 23개 통과
- 지식 공백: 경계 조건을 "같음"으로 쓸지 "이상"으로 쓸지 (이번 사례)
- 다음 과제: 빈 목록과 null seatIds가 서비스에서 개수 검사 전에 겹침 쿼리를 먼저 거치는 문제. 없는 좌석 id 처리(후보 D)
- 레벨 조정 제안: 없음

## 2026-10-29 — seatIds null 거절, 서비스 검증 순서 리팩터링

- 목표: seatIds가 없는 요청은 400으로 거절. 빈 목록 처리 위치 결정 후 서비스 순서 리팩터링
- 사용자 결정: null은 DTO `@NotNull` + `@Valid`. 빈 목록은 도메인(`Hold.create`)이 거절하도록 두고, 서비스에서 `Hold.create`를 DB 겹침 검사보다 먼저 호출 (3번안). 리팩터링 시점은 사용자가 Red-Green-Refactor에 따라 지금으로 정함
- AI 구현 범위: API 테스트 1개(임시 이름), `HoldRequest` `@NotNull`, 컨트롤러 `@Valid`, `HoldService` 순서 변경
- 테스트 결과: RED는 `Ids must not be null` 예외로 실패 확인. 빈 목록은 임시 테스트로 이미 400 `INVALID_SEAT_COUNT`임을 확인 후 임시 테스트 삭제. 리팩터링 후 전체 24개 통과
- 지식 공백: 미확인 (null은 요청 모양 오류, 빈 목록은 업무 규칙 위반이라는 구분은 AI가 설명함)
- 다음 과제: 빈 목록 거절을 지키는 API 테스트 여부. 없는 좌석 id 처리(후보 D). 테스트 이름 사용자 지정
- 레벨 조정 제안: 없음
