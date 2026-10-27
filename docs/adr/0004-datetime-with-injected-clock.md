# 시각은 DATETIME + LocalDateTime(한국 시간)으로 저장하고, 현재 시각은 주입된 Clock에서 읽는다

회차의 시작 시각과 예매 오픈 시각은 `DATETIME` 컬럼과 `LocalDateTime`으로 다룬다. 모든 값은 한국 시간(Asia/Seoul) 기준이다. 앱은 `now()`를 직접 부르지 않고, `Asia/Seoul`로 고정한 `Clock` 빈에서 현재 시각을 읽는다. 테스트는 이 빈을 시각을 직접 정하거나 앞으로 돌릴 수 있는 시계로 바꾼다.

**선택 이유 (사용자)**: 테스트 용이성과 장소에 따른 시간 차이를 대비하기 위해 Clock 빈을 주입하는 방향으로 간다.

## Considered Options

- **`TIMESTAMP` + `Instant`(UTC)**: 시점을 정확히 표현한다. 하지만 MySQL이 커넥션 타임존에 따라 값을 변환하므로, 로컬 MySQL과 Testcontainers MySQL의 설정이 다르면 같은 값이 다르게 읽힐 수 있다. 2038년까지만 저장할 수 있다는 한계도 있다. 채택하지 않았다.

## Consequences

- 값에 타임존 정보가 없으므로 "모든 시각은 한국 시간"이라는 약속을 지켜야 한다. 해외 공연을 다루게 되면 다시 판단한다.
