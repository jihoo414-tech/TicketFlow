# 외부 시스템 호출 코드는 도메인 안 `client` 폴더에 둔다

PG(토스페이먼츠) 결제 승인처럼 외부 시스템을 HTTP로 호출하는 코드는 그 규칙을 소유한 도메인 안의 `client` 폴더에 둔다. 결제→예매는 `booking`이 소유하므로(ADR 0001) 토스 호출 코드는 `domain/booking/client/`에 둔다. ADR 0001의 내부 레이어 `controller` / `service` / `repository` / `entity`에 `client`를 더한다.

```
domain/booking/
├── controller/
├── service/
├── repository/
├── entity/
└── client/      TossPaymentsClient (인터페이스) + 실제 구현
```

HTTP 호출은 Spring 내장 `RestClient`로 하며, 의존성은 추가하지 않는다. 시크릿 키는 `application.yml`에 적지 않고 환경변수로 주입한다. 테스트에서는 `TossPaymentsClient`를 Fake 구현으로 바꿔 끼운다.

**선택 이유 (사용자)**: 대화에서 AI 추천에 동의했다. 직접 쓴 이유로 바꿀 것.

## Considered Options

- **`booking/service` 안에 두기**: 레이어를 늘리지 않지만, 좌석 규칙을 조립하는 서비스와 외부 HTTP·JSON 변환 코드가 한 폴더에 섞인다.
- **`global/` 아래에 두기**: 여러 도메인이 함께 쓸 수 있지만, 결제는 booking 규칙의 일부라서 "규칙의 소유자별 패키지" 원칙과 맞지 않는다. `global/`은 exception, config, init 세 개로 제한되어 있다.

## Consequences

- 다른 도메인이 외부 시스템을 호출하게 되면 같은 규칙으로 그 도메인 안에 `client`를 둔다.
- `client`는 외부 응답을 우리 쪽 결과 값으로 바꿔 돌려주는 일까지만 한다. 그 결과로 선점·예매 상태를 어떻게 바꿀지는 서비스와 엔티티가 정한다.
- 서비스는 인터페이스에 의존하므로, 기능·동시성 테스트에서 실제 토스를 호출하지 않고 성공·실패·지연·타임아웃을 재현할 수 있다.
