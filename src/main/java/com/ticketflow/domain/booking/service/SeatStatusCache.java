package com.ticketflow.domain.booking.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

@Component
public class SeatStatusCache {

    private static final Duration TIME_TO_LIVE = Duration.ofSeconds(1);
    private static final int MAXIMUM_SHOWTIMES = 100;

    private final HoldService holdService;
    private final JsonMapper jsonMapper;
    private final Cache<Long, byte[]> cache;

    public SeatStatusCache(HoldService holdService, JsonMapper jsonMapper, Ticker ticker) {
        this.holdService = holdService;
        this.jsonMapper = jsonMapper;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TIME_TO_LIVE)
                .maximumSize(MAXIMUM_SHOWTIMES)
                .ticker(ticker)
                .build();
    }

    public byte[] getSeats(Long showtimeId) {
        return cache.get(showtimeId, this::serializeSeats);
    }

    public void clear() {
        cache.invalidateAll();
    }

    private byte[] serializeSeats(Long showtimeId) {
        return jsonMapper.writeValueAsBytes(holdService.getSeats(showtimeId));
    }
}
