# 좌석 점유를 바꾸는 작업은 `SeatOccupancyLock.acquireMemberAndSeats` 한 곳에서 락을 잡는다

ADR 0008은 좌석 점유를 바꾸는 모든 작업(선점, 선점 취소, 결제, 예매 취소)이 지켜야 할 순서를 정했다. 하지만 그 순서를 선점(`HoldService.hold`)과 결제(`ReservationService.pay`)가 각자 조립하고 있었다. 회원 락, 좌석 락, 격리 수준, 락 직후 시각 읽기를 메서드마다 따로 챙기는 구조라서, 선점 취소나 예매 취소를 추가할 때 하나를 빠뜨려도 컴파일과 단건 테스트는 통과한다. 그래서 순서를 `booking/service`의 `SeatOccupancyLock` 한 곳으로 모은다. 락 순서와 락 범위는 ADR 0008 그대로 둔다.

`acquireMemberAndSeats(memberId, seatIds)`는 다음을 이 순서대로 한다.

1. 회원 행을 `SELECT … FOR UPDATE`로 잠근다.
2. 좌석 행들을 id 오름차순으로 `SELECT … FOR UPDATE`로 잠근다.
3. 락을 모두 잡은 직후의 시각을 읽는다.

결과는 `LockedOccupancy(member, seats, lockedAt)`로 돌려준다. 호출하는 쪽은 `lockedAt`을 그 작업의 기준 시각으로 쓴다. 결제에서는 이 값이 불변식 10의 "결제 요청 시각"이다.

호출하는 쪽에 남는 책임은 세 가지다.

- 트랜잭션을 `@Transactional(isolation = Isolation.READ_COMMITTED)`로 연다. `acquireMemberAndSeats`는 이미 열린 트랜잭션 안에서 락을 잡고, 락은 그 트랜잭션이 끝날 때 풀린다.
- 선점이나 예매 엔티티는 `acquireMemberAndSeats` **뒤에** 읽는다. 락 전에 읽으면 영속성 컨텍스트에 옛 상태가 남는다(ADR 0008 Consequences).
- `acquireMemberAndSeats`에 넘길 좌석 id는 한번 만들어진 뒤 바뀌지 않는 데이터(`hold_seat`)에서만 구한다. 그래야 락 없이 읽어도 결과가 같다.

## 근거

- **규칙의 주인은 booking이다.** "회원 → 좌석 순서로 잠근다"는 회원 규칙도 좌석 위치 규칙도 아니다. 좌석 상태 머신의 규칙이다. 그래서 `MemberService`나 `SeatService`가 아니라 booking에 둔다(ADR 0001). 실제로 락을 거는 일은 지금처럼 각 행의 주인 서비스(`findLockedMember`, `findLockedSeats`)를 거친다.
- **새 레이어가 아니다.** 기존 `service` 폴더 안의 클래스 하나다. 업무 규칙을 판단하지 않고 순서만 지키는 부품이라 `@Service` 대신 `@Component`로 둔다.
- **격리 수준은 호출하는 쪽이 정한다.** Spring은 트랜잭션이 처음 시작될 때만 `isolation`을 적용하고, 이미 열린 트랜잭션에 합류할 때는 무시한다. 그래서 `acquireMemberAndSeats`가 격리 수준을 직접 정할 수 없다.
- **시각을 락과 함께 돌려준다.** 불변식 10의 시각은 "락을 잡은 직후"로 정의된다. 시각을 호출하는 쪽에서 따로 읽으면, 누군가 그 줄을 락 앞으로 옮겨도 아무도 모른다. 시각을 락의 결과로 받게 하면 그런 실수를 할 수 없다.
- **순서를 확인할 곳이 하나가 된다.** 리뷰할 때 새 작업이 `acquireMemberAndSeats`를 부르는지만 보면 된다. 메서드 본문의 줄 순서를 하나하나 확인하지 않아도 된다.

## Considered Options

- **콜백형** `seatOccupancyLock.run(memberId, seatIds, locked -> { ... })`: 엔티티를 락 뒤에 읽는 것까지 구조로 강제할 수 있다. 하지만 서비스 메서드 본문이 람다 안으로 들어가서, 메서드가 이름 붙은 단계로 읽히지 않는다. 지금 서비스 메서드를 쓰는 방식과 맞지 않는다.
- **어노테이션 + AOP** (`@SeatOccupancyLocked`): 호출하는 쪽 코드가 가장 짧다. 하지만 새 기술(Aspect)이 들어오고, 어떤 인자가 회원 id이고 좌석 id인지 리플렉션으로 찾아야 한다. `@Transactional` 프록시와의 실행 순서도 따로 관리해야 한다. 순서를 "한눈에 보이게" 하려는 목적과 반대다.
- **서비스 상위 클래스(템플릿 메서드)**: 상속으로 순서를 강제한다. 하지만 선점, 결제, 취소의 흐름이 서로 달라서 공통 골격이 억지스럽고, 서비스 사이에 상속 관계가 새로 생긴다.
- **`acquireMemberAndSeats`에서 현재 트랜잭션이 `READ COMMITTED`인지 확인하고 아니면 예외**: 어노테이션을 빠뜨린 코드를 테스트에서 잡을 수 있다. 하지만 API 테스트는 클래스에 `@Transactional`을 붙여 테스트가 기본 격리 수준 트랜잭션을 열고, 서비스가 거기에 합류한다(ADR 0005). 그래서 확인을 넣자 선점·결제 API 테스트가 모두 실패했다. 테스트 전략을 함께 바꿔야 하므로 이번 결정에서는 뺐다.
- **코드는 그대로 두고 ADR에 체크리스트만 추가**: 가장 가볍다. 하지만 새 기능을 만들 때 빠뜨리는 걸 막지 못해서 이번 문제를 풀지 못한다.
- **`SeatService.findLockedSeats`가 회원 락까지 잡기**: 새 파일이 없다. 하지만 performance 패키지가 회원과 booking의 규칙을 알게 되어 주인이 어긋난다.

## Consequences

- 선점에서 회차 조회가 좌석 락 **뒤로** 옮겨진다. 없는 회차로 요청해도 좌석 락을 잠깐 잡는다. 응답 에러 코드는 그대로다.
- 결제에서 선점의 좌석 id 조회가 회원 락 **앞으로** 옮겨진다. `hold_seat`는 만들어진 뒤 바뀌지 않으므로 결과는 같다.
- `HoldService`와 `ReservationService`는 락을 잡으려고 `MemberService`를 직접 부르지 않는다.
- 좌석 점유를 바꾸는 새 경로(선점 취소, 예매 취소, 나중에 생길 만료 정리 작업)는 반드시 `SeatOccupancyLock`(`acquireMemberAndSeats` 또는 `acquireSeats`)을 거쳐야 한다. 거치지 않는 경로는 이 결정과 ADR 0008을 모두 우회한다.
- 격리 수준을 빠뜨려도 코드가 알려 주지 않는다. 이 부분은 여전히 리뷰로 확인해야 한다.
- API 테스트에서는 서비스가 테스트 트랜잭션(기본 격리 수준)에 합류하므로 `READ COMMITTED`로 실행되지 않는다. `READ COMMITTED`에서의 동작은 트랜잭션을 붙이지 않은 동시성 테스트만 확인한다.

## 추가: 회원 락이 필요 없는 작업은 `acquireSeats`를 쓴다

회원 행 락은 회원 단위 규칙(회차당 선점 하나, 4석 제한)을 지키려고 잡는다. 같은 회원의 두 요청이 서로 다른 좌석을 노리면 좌석 락에서 만나지 않으므로, 회원 행이 둘이 만나는 지점이 된다.

선점 취소는 이 규칙을 깰 수 없다. 좌석 수가 줄기만 하고, 같은 선점을 건드리는 결제·취소와는 같은 좌석 행에서 만난다. 그래서 `SeatOccupancyLock`에 회원 락 없이 좌석만 잠그는 `acquireSeats(seatIds)`를 두고, 선점 취소는 이것을 쓴다.

- `acquireSeats`는 좌석 행을 id 오름차순으로 `SELECT … FOR UPDATE`로 잠그고, 그 직후 시각을 읽어 `LockedSeats(seats, lockedAt)`로 돌려준다.
- `acquireMemberAndSeats`는 회원 행을 잠근 뒤 `acquireSeats`를 부른다. 락 순서(회원 → 좌석)는 그대로다.
- 좌석만 잠그는 작업은 회원 락을 쥐지 않으므로, 회원 → 좌석 순서로 잠그는 작업과 교착 상태를 만들지 않는다.
- 호출하는 쪽의 책임(`READ COMMITTED`, 락 뒤에 엔티티 읽기, 바뀌지 않는 데이터에서 좌석 id 구하기)은 `acquireMemberAndSeats`와 같다.
- 회원 존재 확인은 `CurrentMemberIdArgumentResolver`가 요청마다 하므로, 회원 행을 읽지 않아도 없는 회원은 거절된다.
- 결제는 아직 `acquireMemberAndSeats`를 쓴다.

### 예매 취소도 `acquireSeats`를 쓴다

예매 취소도 좌석 수를 줄이기만 하므로 회원 단위 규칙을 깰 수 없다. 같은 예매를 건드리는 다른 취소, 그 좌석을 노리는 다른 회원의 선점과는 같은 좌석 행에서 만난다.

- 순서: 예매를 읽어 선점 id를 얻는다 → `hold_seat`에서 좌석 id를 구한다 → `acquireSeats` → 선점을 읽는다 → 소유자를 확인한다 → `hold.cancelReservation(lockedAt)`.
- 예매(`reservation`) 행은 락 전에 읽는다. 예매 행에는 바뀌는 값이 없어서(상태는 선점에 있다, ADR 0012) 락 없이 읽어도 결과가 같다. 상태가 담긴 선점은 락 뒤에 읽는다.
- 대가: 같은 회원이 예매 취소와 새 선점을 동시에 보내면 둘은 회원 행에서 만나지 않는다. 새 선점이 취소가 커밋되기 전의 석 수를 보고 4석 제한으로 거절될 수 있다. 결과는 "선점 → 취소" 순서로 실행한 것과 같아서 불변식은 깨지지 않는다. 회원은 다시 시도하면 된다.
