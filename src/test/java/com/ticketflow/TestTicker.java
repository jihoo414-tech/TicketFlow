package com.ticketflow;

import com.github.benmanes.caffeine.cache.Ticker;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public class TestTicker implements Ticker {

    private final AtomicLong nanos = new AtomicLong();

    public void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Override
    public long read() {
        return nanos.get();
    }
}
