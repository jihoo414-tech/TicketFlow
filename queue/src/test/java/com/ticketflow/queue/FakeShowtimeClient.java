package com.ticketflow.queue;

import com.ticketflow.queue.domain.queue.client.ShowtimeClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeShowtimeClient implements ShowtimeClient {

    private final Map<Long, LocalDateTime> bookingOpenAts = new ConcurrentHashMap<>();

    public void add(Long showtimeId, LocalDateTime bookingOpenAt) {
        bookingOpenAts.put(showtimeId, bookingOpenAt);
    }

    @Override
    public Optional<LocalDateTime> findBookingOpenAt(Long showtimeId) {
        return Optional.ofNullable(bookingOpenAts.get(showtimeId));
    }
}
