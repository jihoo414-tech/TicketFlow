package com.ticketflow.concurrency;

import com.ticketflow.TestClock;
import com.ticketflow.TestcontainersConfiguration;
import com.ticketflow.domain.booking.repository.HoldRepository;
import com.ticketflow.domain.booking.repository.HoldSeatRepository;
import com.ticketflow.domain.booking.repository.ReservationRepository;
import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
import com.ticketflow.domain.performance.repository.PerformanceRepository;
import com.ticketflow.domain.performance.repository.SeatRepository;
import com.ticketflow.domain.performance.repository.ShowtimeRepository;
import com.ticketflow.domain.performance.repository.ZoneRepository;
import com.ticketflow.fixture.AuthHeaders;
import com.ticketflow.fixture.EntryPassTokens;
import com.ticketflow.fixture.HoldFixture;
import com.ticketflow.fixture.HoldFixtureFactory;
import com.ticketflow.fixture.TestMembers;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.ticketflow.domain.admission.service.EntryPasses.ENTRY_TOKEN;
import static com.ticketflow.fixture.HoldFixtureFactory.BOOKING_OPEN_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 좌석 잠금 순서(회원 → 좌석 ID 오름차순)가 순환 대기를 막는지 실제 MySQL에서 확인한다 (#67).
 * <p>
 * 판정은 두 가지로 한다. 요청 단위로는 201·409 외의 응답이나 예외(데드락, 락 대기 타임아웃)가 없어야 하고,
 * DB 단위로는 InnoDB 데드락 카운터(information_schema.INNODB_METRICS lock_deadlocks)가 늘지 않아야 한다.
 * 카운터가 실제 데드락을 잡는지는 잠금 순서를 일부러 뒤집은 대조 실험으로 먼저 확인한다.
 * <p>
 * 검증 범위는 아래 시나리오와 반복 횟수에 한정되며, 모든 경로에서 데드락이 불가능하다는 증명은 아니다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SeatLockDeadlockTest {

    private static final int REVERSED_ROUNDS = 50;
    private static final int OVERLAP_ROUNDS = 20;
    private static final int OVERLAP_SEAT_COUNT = 8;
    private static final int OVERLAP_MEMBER_COUNT = 16;
    private static final int MIXED_ROUNDS = 30;
    private static final long REQUEST_TIMEOUT_SECONDS = 20;

    @Autowired MockMvc mockMvc;
    @Autowired EntryPassTokens entryPassTokens;
    @Autowired AuthHeaders authHeaders;
    @Autowired TestClock testClock;
    @Autowired HoldFixtureFactory holdFixtureFactory;
    @Autowired MySQLContainer mysqlContainer;
    @Autowired MemberRepository memberRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired HoldSeatRepository holdSeatRepository;
    @Autowired HoldRepository holdRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired ShowtimeRepository showtimeRepository;
    @Autowired PerformanceRepository performanceRepository;

    @BeforeEach
    void enableDeadlockMetric() throws SQLException {
        try (Connection root = rootConnection(); Statement statement = root.createStatement()) {
            statement.execute("SET GLOBAL innodb_monitor_enable = 'lock_deadlocks'");
        }
    }

    @AfterEach
    void cleanUp() {
        testClock.reset();
        reservationRepository.deleteAllInBatch();
        holdSeatRepository.deleteAllInBatch();
        holdRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        zoneRepository.deleteAllInBatch();
        showtimeRepository.deleteAllInBatch();
        performanceRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void 대조_실험_좌석을_요청_순서대로_잠그면_데드락이_발생하고_카운터가_이를_기록한다() throws Exception {
        HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
        Long first = fixture.seatIds().get(0);
        Long second = fixture.seatIds().get(1);
        long before = deadlockCount();

        CyclicBarrier bothLockedFirstSeat = new CyclicBarrier(2);
        List<Callable<String>> transactions = List.of(
                () -> lockInGivenOrder(List.of(first, second), bothLockedFirstSeat),
                () -> lockInGivenOrder(List.of(second, first), bothLockedFirstSeat));
        List<String> outcomes = runConcurrently(transactions);

        assertThat(outcomes).containsExactlyInAnyOrder("COMMITTED", "DEADLOCK");
        assertThat(deadlockCount() - before).isEqualTo(1);
    }

    @Test
    void 같은_좌석들을_서로_반대_순서로_동시에_선점해도_데드락_없이_한_명만_선점한다() throws Exception {
        long before = deadlockCount();

        for (int round = 0; round < REVERSED_ROUNDS; round++) {
            HoldFixture fixture = holdFixtureFactory.createHoldFixture(2);
            List<Long> seatIds = fixture.seatIds();
            Member memberA = memberRepository.save(TestMembers.member("역순A" + round));
            Member memberB = memberRepository.save(TestMembers.member("역순B" + round));
            testClock.setTo(BOOKING_OPEN_AT);

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> holdOutcome(fixture, memberA, List.of(seatIds.get(0), seatIds.get(1))),
                    () -> holdOutcome(fixture, memberB, List.of(seatIds.get(1), seatIds.get(0)))));

            assertThat(outcomes).extracting(Outcome::label).containsExactlyInAnyOrder("201", "409");
            assertNoSeatOccupiedTwice(seatIds);
            cleanUp();
        }

        assertThat(deadlockCount() - before).isZero();
    }

    @Test
    void 여러_회원이_겹치는_좌석_묶음을_섞인_순서로_동시에_선점해도_데드락이_없다() throws Exception {
        Random random = new Random(67);
        long before = deadlockCount();
        int requestCount = 0;
        int heldCount = 0;

        for (int round = 0; round < OVERLAP_ROUNDS; round++) {
            HoldFixture fixture = holdFixtureFactory.createHoldFixture(OVERLAP_SEAT_COUNT);
            List<Long> seatIds = fixture.seatIds();
            List<Member> members = memberRepository.saveAll(IntStream.range(0, OVERLAP_MEMBER_COUNT)
                    .mapToObj(no -> TestMembers.member("겹침" + no))
                    .toList());
            testClock.setTo(BOOKING_OPEN_AT);

            List<Callable<Outcome>> requests = new ArrayList<>();
            for (Member member : members) {
                List<Long> picked = pickShuffled(seatIds, 2 + random.nextInt(3), random);
                requests.add(() -> holdOutcome(fixture, member, picked));
            }
            List<Outcome> outcomes = runConcurrently(requests);

            assertThat(outcomes).extracting(Outcome::label).containsOnly("201", "409");
            assertNoSeatOccupiedTwice(seatIds);
            requestCount += outcomes.size();
            heldCount += (int) outcomes.stream().filter(outcome -> outcome.label().equals("201")).count();
            cleanUp();
        }

        System.out.printf("[#67] 겹치는 묶음: %d회 × %d명 = 요청 %d건, 선점 성공 %d건%n",
                OVERLAP_ROUNDS, OVERLAP_MEMBER_COUNT, requestCount, heldCount);
        assertThat(deadlockCount() - before).isZero();
    }

    @Test
    void 결제와_선점_취소와_다른_회원의_역순_선점이_동시에_일어나도_데드락이_없다() throws Exception {
        long before = deadlockCount();

        for (int round = 0; round < MIXED_ROUNDS; round++) {
            HoldFixture fixture = holdFixtureFactory.createHoldFixture(3);
            List<Long> seatIds = fixture.seatIds();
            Member owner = fixture.member();
            Member memberB = memberRepository.save(TestMembers.member("혼합B" + round));
            Member memberC = memberRepository.save(TestMembers.member("혼합C" + round));
            testClock.setTo(BOOKING_OPEN_AT);
            Long holdId = createHold(fixture, owner, List.of(seatIds.get(0), seatIds.get(1)));

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> outcome(() -> mockMvc.perform(post("/api/holds/{holdId}/payment", holdId)
                            .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(owner.getId())))
                            .andReturn().getResponse()),
                    () -> outcome(() -> mockMvc.perform(post("/api/holds/{holdId}/cancellation", holdId)
                            .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(owner.getId())))
                            .andReturn().getResponse()),
                    () -> holdOutcome(fixture, memberB, List.of(seatIds.get(1), seatIds.get(0))),
                    () -> holdOutcome(fixture, memberC, List.of(seatIds.get(2), seatIds.get(1)))));

            assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.label()).matches("2\\d\\d|409"));
            assertNoSeatOccupiedTwice(seatIds);
            cleanUp();
        }

        assertThat(deadlockCount() - before).isZero();
    }

    private Outcome holdOutcome(HoldFixture fixture, Member member, List<Long> seatIds) {
        return outcome(() -> holdRequest(fixture, member, seatIds));
    }

    private Long createHold(HoldFixture fixture, Member member, List<Long> seatIds) throws Exception {
        MockHttpServletResponse response = holdRequest(fixture, member, seatIds);
        assertThat(response.getStatus()).isEqualTo(201);
        return ((Number) JsonPath.read(response.getContentAsString(), "$.id")).longValue();
    }

    private MockHttpServletResponse holdRequest(HoldFixture fixture, Member member, List<Long> seatIds) throws Exception {
        return mockMvc.perform(post("/api/showtimes/{showtimeId}/holds", fixture.showtime().getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeaders.bearer(member.getId()))
                        .header(ENTRY_TOKEN, entryPassTokens.issue(member.getId(), fixture.showtime().getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds": %s}
                                """.formatted(seatIds)))
                .andReturn()
                .getResponse();
    }

    /** 응답 코드를, 처리되지 않은 예외(데드락·락 대기 타임아웃 등)는 예외 이름을 label로 남긴다. */
    private Outcome outcome(Callable<MockHttpServletResponse> request) {
        try {
            return new Outcome(String.valueOf(request.call().getStatus()));
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            return new Outcome(e.getClass().getSimpleName() + " / " + root.getClass().getSimpleName() + ": " + root.getMessage());
        }
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                // 제한 시간 안에 끝나지 않으면 TimeoutException으로 실패한다.
                results.add(future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertNoSeatOccupiedTwice(List<Long> seatIds) {
        LocalDateTime now = LocalDateTime.now(testClock);
        Map<Long, Long> occupyingCountBySeat = holdSeatRepository.findAllWithHoldBySeatIdIn(seatIds).stream()
                .filter(holdSeat -> holdSeat.isOccupying(now))
                .collect(Collectors.groupingBy(holdSeat -> holdSeat.getSeat().getId(), Collectors.counting()));
        assertThat(occupyingCountBySeat.values()).allSatisfy(count -> assertThat(count).isEqualTo(1L));
    }

    private static List<Long> pickShuffled(List<Long> seatIds, int count, Random random) {
        List<Long> copy = new ArrayList<>(seatIds);
        Collections.shuffle(copy, random);
        return List.copyOf(copy.subList(0, count));
    }

    /** 대조군: 정렬 없이 주어진 순서대로 좌석 행을 하나씩 잠근다. 두 트랜잭션이 첫 좌석을 잡은 뒤 교차한다. */
    private String lockInGivenOrder(List<Long> seatIds, CyclicBarrier bothLockedFirstSeat) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                mysqlContainer.getJdbcUrl(), mysqlContainer.getUsername(), mysqlContainer.getPassword())) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM seat WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, seatIds.get(0));
                lock.executeQuery().close();
                bothLockedFirstSeat.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                lock.setLong(1, seatIds.get(1));
                lock.executeQuery().close();
                connection.commit();
                return "COMMITTED";
            } catch (SQLException e) {
                connection.rollback();
                // MySQL ER_LOCK_DEADLOCK = 1213
                return e.getErrorCode() == 1213 ? "DEADLOCK" : "SQL " + e.getErrorCode() + ": " + e.getMessage();
            }
        }
    }

    private long deadlockCount() throws SQLException {
        try (Connection root = rootConnection();
             Statement statement = root.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT `COUNT` FROM information_schema.INNODB_METRICS WHERE NAME = 'lock_deadlocks'")) {
            result.next();
            return result.getLong(1);
        }
    }

    private Connection rootConnection() throws SQLException {
        return DriverManager.getConnection(mysqlContainer.getJdbcUrl(), "root", mysqlContainer.getPassword());
    }

    private record Outcome(String label) {
    }
}
