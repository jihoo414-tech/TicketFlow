# Graph Report - ticketflowwithai2  (2026-10-29)

## Corpus Check
- 79 files · ~20,621 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 372 nodes · 968 edges · 20 communities (14 shown, 6 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 65 edges (avg confidence: 0.82)
- Token cost: 115,541 input · 0 output

## Community Hubs (Navigation)
- Hold Service & Repositories
- Integration & Concurrency Tests
- Project Rules & ADRs
- JPA Entities
- Hold Domain Unit Tests
- Performance Catalog & Seed Data
- Booking Controllers & DTOs
- Web Config & Member Resolver
- Member Domain
- Hold Invariants & Concurrency Cases
- Global Exception Handling
- Gradle Wrapper Script
- Application Entry Point
- Test Application Entry
- Learning Records
- Load Smoke Test
- Prompt Log
- Booking Web UI

## God Nodes (most connected - your core abstractions)
1. `Seat` - 39 edges
2. `Showtime` - 36 edges
3. `Member` - 31 edges
4. `BusinessException` - 24 edges
5. `ErrorCode` - 23 edges
6. `HoldService` - 22 edges
7. `Performance` - 21 edges
8. `Hold` - 20 edges
9. `HoldConcurrencyTest` - 19 edges
10. `PerformanceApiTest` - 17 edges

## Surprising Connections (you probably didn't know these)
- `Goal: Learning over Portfolio, User Understands Code` --rationale_for--> `ADR 0001: Owner-based Domain Packages + 3 Layers`  [INFERRED]
  prompt.md → docs/adr/0001-domain-package-structure.md
- `TicketFlow Project Rules (CLAUDE.md)` --references--> `Learning Notes (user-only)`  [EXTRACTED]
  CLAUDE.md → docs/learning-notes.md
- `Goal: Learning over Portfolio, User Understands Code` --rationale_for--> `Work Unit: One Behavior (~200-line diff)`  [INFERRED]
  prompt.md → CLAUDE.md
- `hold / hold_seat / reservation Table Design` --implements--> `Held Seat (선점 좌석)`  [INFERRED]
  docs/concurrency-cases.md → GLOSSARY.md
- `startHoldTimer()` --conceptually_related_to--> `Hold Expiry (선점 만료)`  [INFERRED]
  src/main/resources/static/index.html → GLOSSARY.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Seat Occupancy Row-Lock Protocol** — docs_adr_0008_serialize_seat_occupancy_with_row_locks_row_lock_serialization, docs_adr_0003_seat_row_per_showtime_seat_row_per_showtime, docs_concurrency_cases_pessimistic_lock, docs_concurrency_cases_hold_operation_flow, docs_concurrency_cases_hold_table_schema [INFERRED 0.85]
- **Concurrency Cases Attacking Invariants** — docs_concurrency_cases_two_members_one_seat, docs_concurrency_cases_partial_overlap_bundles, docs_concurrency_cases_same_member_two_tabs, docs_concurrency_cases_double_payment, docs_concurrency_cases_cancel_vs_payment, docs_concurrency_cases_reservation_cancel_vs_new_hold, docs_concurrency_cases_payment_vs_expiry [EXTRACTED 1.00]
- **Test Infrastructure Decisions** — docs_adr_0002_mysql_only_no_h2_mysql_only, docs_adr_0004_datetime_with_injected_clock_testclock, docs_adr_0005_test_strategy_three_test_kinds_no_mock, docs_adr_0009_test_fixture_package_fixture_package [INFERRED 0.85]

## Communities (20 total, 6 thin omitted)

### Community 0 - "Hold Service & Repositories"
Cohesion: 0.06
Nodes (29): org.springframework.data.jpa.repository.JpaRepository, org.springframework.data.jpa.repository.Lock, org.springframework.data.jpa.repository.Query, org.springframework.stereotype.Service, org.springframework.web.bind.annotation.PostMapping, HoldResponse, HoldSeatResponse, SeatResponse (+21 more)

### Community 1 - "Integration & Concurrency Tests"
Cohesion: 0.09
Nodes (20): MySQLContainer, org.junit.jupiter.api.AfterEach, org.springframework.boot.test.context.SpringBootTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.boot.testcontainers.service.connection.ServiceConnection, org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Import (+12 more)

### Community 2 - "Project Rules & ADRs"
Cohesion: 0.05
Nodes (53): New Package/Layer/Tech Requires User Approval + ADR, Stack: Java 21, Spring Boot 4, JPA, MySQL, Gradle, TicketFlow Project Rules (CLAUDE.md), Work Unit: One Behavior (~200-line diff), X-Member-Id Header Identification, MySQL 8.4 Docker Service, booking package (seat state machine owner), global/init local-profile seed data (+45 more)

### Community 3 - "JPA Entities"
Cohesion: 0.09
Nodes (19): lombok.Getter, lombok.NoArgsConstructor, ShowtimeResponse, Entity, Table, Showtime, ShowtimeRepository, ShowtimeService (+11 more)

### Community 4 - "Hold Domain Unit Tests"
Cohesion: 0.16
Nodes (4): org.junit.jupiter.api.Test, HoldTest, ShowtimeTest, PerformanceApiTest

### Community 5 - "Performance Catalog & Seed Data"
Cohesion: 0.15
Nodes (14): org.springframework.boot.ApplicationArguments, org.springframework.boot.ApplicationRunner, org.springframework.context.annotation.Profile, org.springframework.stereotype.Component, PerformanceResponse, Entity, Table, Performance (+6 more)

### Community 6 - "Booking Controllers & DTOs"
Cohesion: 0.19
Nodes (13): java.lang.annotation.Retention, java.lang.annotation.Target, lombok.RequiredArgsConstructor, org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.RestController, HoldController, SeatController (+5 more)

### Community 7 - "Web Config & Member Resolver"
Cohesion: 0.19
Nodes (13): org.jspecify.annotations.Nullable, org.springframework.context.annotation.Configuration, org.springframework.core.MethodParameter, org.springframework.web.bind.support.WebDataBinderFactory, org.springframework.web.context.request.NativeWebRequest, org.springframework.web.method.support.HandlerMethodArgumentResolver, org.springframework.web.method.support.ModelAndViewContainer, org.springframework.web.servlet.config.annotation.WebMvcConfigurer (+5 more)

### Community 8 - "Member Domain"
Cohesion: 0.24
Nodes (7): org.springframework.transaction.annotation.Transactional, MemberResponse, Entity, Table, Member, MemberRepository, MemberService

### Community 9 - "Hold Invariants & Concurrency Cases"
Cohesion: 0.13
Nodes (15): Case 4-3: Double Payment on Same Hold, hold.status (HELD/CANCELLED/RESERVED/RESERVATION_CANCELLED), hold / hold_seat / reservation Table Design, Case 4-1: Partially Overlapping Bundles, Invariant 11: Reservation cancel only before showtime start, whole unit, Invariant 3: Hold is 1-4 seats, all-or-nothing, Invariant 9: At most one reservation per hold, Slice: Reject 0 or 5+ Seats (INVALID_SEAT_COUNT) (+7 more)

### Community 10 - "Global Exception Handling"
Cohesion: 0.53
Nodes (4): org.springframework.http.ProblemDetail, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice, GlobalExceptionHandler

### Community 11 - "Gradle Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **36 isolated node(s):** `options`, `AVAILABLE`, `HELD`, `HELD`, `CANCELLED` (+31 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **6 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Seat` connect `Hold Service & Repositories` to `Integration & Concurrency Tests`, `JPA Entities`, `Hold Domain Unit Tests`, `Performance Catalog & Seed Data`?**
  _High betweenness centrality (0.069) - this node is a cross-community bridge._
- **Why does `Showtime` connect `JPA Entities` to `Hold Service & Repositories`, `Integration & Concurrency Tests`, `Hold Domain Unit Tests`, `Performance Catalog & Seed Data`?**
  _High betweenness centrality (0.060) - this node is a cross-community bridge._
- **Why does `BusinessException` connect `JPA Entities` to `Hold Service & Repositories`, `Hold Domain Unit Tests`, `Performance Catalog & Seed Data`, `Web Config & Member Resolver`, `Member Domain`, `Global Exception Handling`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **What connects `options`, `AVAILABLE`, `HELD` to the rest of the system?**
  _36 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Hold Service & Repositories` be split into smaller, more focused modules?**
  _Cohesion score 0.06394230769230769 - nodes in this community are weakly interconnected._
- **Should `Integration & Concurrency Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.08834586466165413 - nodes in this community are weakly interconnected._
- **Should `Project Rules & ADRs` be split into smaller, more focused modules?**
  _Cohesion score 0.0517120894479385 - nodes in this community are weakly interconnected._