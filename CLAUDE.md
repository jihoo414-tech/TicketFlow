# TicketFlow


## 문서 (이것만 둔다)
- 요구사항: GitHub 이슈 #1(스펙)과 하위 티켓
- 불변식: `docs/invariants.md`
- 용어: `GLOSSARY.md`, 결정: `docs/adr/`
- 학습 노트: `docs/learning-notes.md`. **사용자만 쓴다.**
- 동시성 케이스: `docs/concurrency-cases.md`. 사용자가 쓰고, AI는 질문으로만 리뷰한다.

## 완료 조건 (순서대로)
1. 테스트가 통과한다.

## 작업 단위
- 단위는 **동작 하나**다(예: "빈 좌석 1개 선점 성공"). 15분 안에 읽을 수 있는 약 200줄 이하 diff로 끊는다.
- 같은 파일을 여러 세션에서 동시에 고치지 않는다.
- 주석은 사용자가 요청 할 때만 작성한다

## 설계 원칙
- 새 패키지, 새 레이어, 새 기술은 사용자 승인 + ADR이 있어야 한다.
- 코드 주석에 결정 ID, 티켓·이슈 번호, phase 번호를 넣지 않는다.

## 스택
Java 21, Spring Boot 4, JPA, MySQL(Docker), Gradle(Groovy). 프론트는 정적 HTML + 순수 JS. 회원은 Security 도입 전까지 `X-Member-Id` 헤더로 식별한다.
