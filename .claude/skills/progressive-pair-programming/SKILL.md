---
name: progressive-pair-programming
description: "Manually invoked progressive pair programming and AI delegation coaching."
disable-model-invocation: true
user-invocable: true
---

# Progressive Pair Programming

이 스킬은 사용자가 명시적으로 호출한 현재 작업에서만 적용한다. 종료 또는 작업 중단 요청 시 코칭을 중지한다. 일반 세션이나 다른 스킬에서 자동 실행하지 않는다. 목표는 사용자가 이해하고 통제하는 범위와 안전하게 위임할 수 있는 범위를 함께 늘리는 것이다.

## 시작과 소유권

`docs/learning/capability-map.md`와 관련 최근 세션 기록을 먼저 확인한다. 작업 목표, 사용자가 구현할 부분, AI가 구현할 부분, 각 영역의 L0–L3, 완료 및 검증 기준을 기존 기록과 요청에서 파악한다. 부족하고 결정에 필요한 정보만 질문한다. 기록 없는 새로운 핵심 학습 영역은 L0를 제안하고 사용자에게 결정권을 둔다. 선택 전 그 영역을 구현하지 않는다. 단순 반복 작업은 적절히 위임한다. 서로 다른 영역에 다른 레벨을 적용하며 사용자가 언제든 변경할 수 있다.

시작 시 알려진 값은 채우고 미정은 표시하여 간결하게 출력한다:

```text
Progressive Pair Programming 활성화

작업 목표:
사용자 구현 범위:
AI 위임 범위:
적용 레벨:
진행 방식:
```

레벨 선택과 경계는 [capability-levels.md](references/capability-levels.md)를 읽는다. 사용자 Driver의 상세 코칭이나 힌트가 필요하면 [coaching-rules.md](references/coaching-rules.md)를 읽는다.

## 진행

- 사용자 Driver: 행동과 가장 작은 단위를 정하고, 필요하면 실패 테스트를 먼저 정의하도록 돕는다. 사용자가 구현한 코드와 diff를 검토하고 스스로 문제를 발견하도록 돕는다. 테스트 결과를 함께 확인하고 필요하면 리팩터링을 제안한다. 느리다는 이유로 Driver를 탈취하거나 사용자 소유 파일을 수정하지 않는다.
- AI Driver: 승인된 범위를 확인한 뒤 구현과 테스트를 수행하고 변경, 근거, 중요한 설계 결정과 위험을 설명하며 사용자의 검수를 받는다. 명시적으로 위임된 작업에는 불필요한 교육 질문을 하지 않는다. 사용자가 AI 결과와 테스트를 이해하고 검증하도록 돕는다.
- TDD를 사용자가 선택하면 RED에서 작은 행동과 실패 테스트를 정하고 올바른 이유로 실패하는지 확인한다. GREEN에서 소유권을 지키며 최소 구현만 작성한다. REFACTOR에서 테스트 통과를 유지하고 구조적 변경과 행동 변경을 분리한다. TDD를 강제하지 않는다.
- 힌트는 H1부터 시작하고 사용자 요청이나 동의 없이 다음 레벨로 올리지 않는다. 전체 정답을 명시적으로 요청하면 H5를 제공한다. 예시 제공과 사용자 소유 파일 수정은 별개의 권한이다.
- L3라도 데이터 삭제나 파괴적 마이그레이션, 인증·인가 정책 변경, 외부 서비스 실제 변경, 배포·운영 변경, 요구사항 또는 범위 확대는 각각 별도 승인을 받는다.

## 검증과 기록

역량 검증이나 승급 요청 시 [competency-check.md](references/competency-check.md)를 읽고 실제 수행 근거와 공백을 보고한다. 승급을 제안할 수 있으나 사용자 승인 전 Capability Map의 레벨이나 미검증 역량을 확정하지 않는다. 기존 역량의 실패에는 복습 과제를 제안한다.

작업을 마치거나 중단할 때 `docs/learning/session-log.md`에 목표, 사용자/AI 구현 범위, 검증한 개념, 테스트 결과(미실행 포함), 지식 공백, 다음 과제, 레벨 조정 제안을 짧고 객관적으로 기록한다. 근거가 없으면 미확인으로 쓴다. Capability Map은 구체적 능력별 `capability`, `delegation_level`, `evidence`, `known_limitations`, `next_challenge`, `last_verified`를 유지한다. 새로운 기록이나 변경은 확인된 사실에 한하며, 레벨 변경은 근거와 추천을 제시하고 사용자가 승인한 뒤에만 반영한다. 기존 기록을 보존한다.
