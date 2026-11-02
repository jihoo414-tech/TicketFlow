package com.ticketflow.queue.domain.queue.service;

import com.ticketflow.queue.global.exception.BusinessException;
import com.ticketflow.queue.global.exception.ErrorCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

public class WaitingQueue {

    private static final Duration PASS_DURATION = Duration.ofMinutes(5);
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);
    private static final int SOON_ROUNDS = 3;
    private static final int SOON_RETRY_SECONDS = 1;
    private static final int LATER_RETRY_SECONDS = 3;

    private final Clock clock;
    private final int admissionLimit;
    private final int admitsPerSecond;
    private final NavigableMap<Long, Waiter> line = new TreeMap<>();
    private final Map<Long, Waiter> waitersByMember = new HashMap<>();
    private final Map<Long, EntryPass> passesByMember = new HashMap<>();
    private final Map<Long, Instant> lastSeenByMember = new HashMap<>();
    private long lastIssuedNumber;
    private long lastAdmittedNumber;

    public WaitingQueue(Clock clock, int admissionLimit, int admitsPerSecond) {
        this.clock = clock;
        this.admissionLimit = admissionLimit;
        this.admitsPerSecond = admitsPerSecond;
    }

    public synchronized QueueStatus join(Long memberId, Long showtimeId) {
        removeExpiredPasses();
        Long currentShowtimeId = currentShowtimeOf(memberId);
        if (currentShowtimeId == null) {
            line(new Waiter(++lastIssuedNumber, memberId, showtimeId));
        } else if (!currentShowtimeId.equals(showtimeId)) {
            throw new BusinessException(ErrorCode.ALREADY_IN_QUEUE);
        }
        return status(memberId);
    }

    public synchronized QueueStatus status(Long memberId) {
        removeExpiredPasses();
        EntryPass pass = passesByMember.get(memberId);
        if (pass != null) {
            return new QueueStatus.Admitted(pass);
        }
        if (!waitersByMember.containsKey(memberId)) {
            return new QueueStatus.NotInQueue();
        }
        lastSeenByMember.put(memberId, clock.instant());
        Waiter waiter = waitersByMember.get(memberId);
        long position = waiter.number() - lastAdmittedNumber - 1;
        return new QueueStatus.Waiting(waiter.showtimeId(), position, retryAfterSeconds(position));
    }

    public synchronized void leave(Long memberId) {
        passesByMember.remove(memberId);
        Waiter waiter = waitersByMember.get(memberId);
        if (waiter != null) {
            remove(waiter);
        }
    }

    public synchronized void admit() {
        removeExpiredPasses();
        removeStaleWaiters();
        int admits = Math.min(admitsPerSecond, admissionLimit - passesByMember.size());
        for (int i = 0; i < admits && !line.isEmpty(); i++) {
            admitNext();
        }
    }

    public synchronized void release(String passId) {
        passesByMember.values().removeIf(pass -> pass.id().equals(passId));
    }

    private int retryAfterSeconds(long position) {
        return position <= (long) admitsPerSecond * SOON_ROUNDS ? SOON_RETRY_SECONDS : LATER_RETRY_SECONDS;
    }

    private Long currentShowtimeOf(Long memberId) {
        EntryPass pass = passesByMember.get(memberId);
        if (pass != null) {
            return pass.showtimeId();
        }
        Waiter waiter = waitersByMember.get(memberId);
        return waiter == null ? null : waiter.showtimeId();
    }

    private void removeExpiredPasses() {
        Instant now = clock.instant();
        passesByMember.values().removeIf(pass -> !now.isBefore(pass.expiresAt()));
    }

    private void removeStaleWaiters() {
        Instant staleBefore = clock.instant().minus(STALE_AFTER);
        line.values().stream()
                .filter(waiter -> !lastSeenByMember.get(waiter.memberId()).isAfter(staleBefore))
                .toList()
                .forEach(this::remove);
    }

    private void line(Waiter waiter) {
        line.put(waiter.number(), waiter);
        waitersByMember.put(waiter.memberId(), waiter);
    }

    private void admitNext() {
        Waiter waiter = line.firstEntry().getValue();
        remove(waiter);
        passesByMember.put(waiter.memberId(), issuePass(waiter));
        lastAdmittedNumber = waiter.number();
    }

    private void remove(Waiter waiter) {
        line.remove(waiter.number());
        waitersByMember.remove(waiter.memberId());
        lastSeenByMember.remove(waiter.memberId());
    }

    private EntryPass issuePass(Waiter waiter) {
        return new EntryPass(
                UUID.randomUUID().toString(),
                waiter.memberId(),
                waiter.showtimeId(),
                clock.instant().plus(PASS_DURATION)
        );
    }

    private record Waiter(long number, Long memberId, Long showtimeId) {
    }
}
