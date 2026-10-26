# 도메인별 패키지 + 내부 3레이어 구조로 고정

이전 두 번의 시도에서 AI가 패키지와 레이어를 계속 늘리는 바람에 구조를 따라갈 수 없게 되었다. 그래서 첫 코드를 쓰기 전에 구조를 고정한다. 각 도메인은 `domain/` 아래 하나의 패키지(`domain/performance`, `domain/hold`, `domain/reservation` 등)를 가지며, 그 안에는 `controller` / `service` / `repository`만 둔다. 특정 도메인에 속하지 않는 코드는 `domain/`과 나란히 있는 `global/`에 두되, `global/exception`과 `global/config` 두 개만 허용한다. 기본 패키지는 `com.ticketflow`다. 헥사고날이나 클린 아키텍처는 쓰지 않는다. 새 도메인 패키지나 새 레이어를 추가하려면 사용자 승인과 ADR이 필요하다.
