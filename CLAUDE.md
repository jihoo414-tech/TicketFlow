# TicketFlow


## 문서 (이것만 둔다)
- 요구사항: GitHub 이슈 #1(스펙)과 하위 티켓
- 불변식: `docs/invariants.md`
- 용어: `GLOSSARY.md`, 결정: `docs/adr/`
- 동시성 케이스: `docs/concurrency-cases.md`. 사용자가 쓰고, AI는 질문으로만 리뷰한다.

## 완료 조건 (순서대로)
1. 테스트가 통과한다.

## 작업 단위
- 같은 파일을 여러 세션에서 동시에 고치지 않는다.
- 주석은 사용자가 요청 할 때만 작성한다

## 설계 원칙
- 새 패키지, 새 레이어, 새 기술은 사용자 승인 + ADR이 있어야 한다.
- 코드 주석에 결정 ID, 티켓·이슈 번호, phase 번호를 넣지 않는다.

## 스택
Java 21, Spring Boot 4, JPA, MySQL(Docker), Gradle(Groovy). 프론트는 React + Vite + TypeScript + Tailwind CSS(빌드 결과물을 Spring이 서빙). 회원은 Security 도입 전까지 `X-Member-Id` 헤더로 식별한다.
