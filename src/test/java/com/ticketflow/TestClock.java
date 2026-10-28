package com.ticketflow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

public class TestClock extends Clock {

    public static final LocalDateTime DEFAULT_NOW = LocalDateTime.of(2026, 10, 1, 9, 0, 0);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final AtomicReference<Instant> now = new AtomicReference<>();

    public TestClock() {
        reset();
    }

    public void setTo(LocalDateTime dateTime) {
        now.set(dateTime.atZone(ZONE).toInstant());
    }

    public void advance(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    public void reset() {
        setTo(DEFAULT_NOW);
    }

    @Override
    public ZoneId getZone() {
        return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
