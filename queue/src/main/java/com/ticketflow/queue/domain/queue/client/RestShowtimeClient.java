package com.ticketflow.queue.domain.queue.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RestShowtimeClient implements ShowtimeClient {

    private final RestClient restClient;
    private final Map<Long, LocalDateTime> bookingOpenAts = new ConcurrentHashMap<>();

    public RestShowtimeClient(@Value("${booking-server.url}") String bookingServerUrl) {
        this.restClient = RestClient.create(bookingServerUrl);
    }

    @Override
    public Optional<LocalDateTime> findBookingOpenAt(Long showtimeId) {
        LocalDateTime cached = bookingOpenAts.get(showtimeId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<LocalDateTime> fetched = fetchBookingOpenAt(showtimeId);
        fetched.ifPresent(bookingOpenAt -> bookingOpenAts.put(showtimeId, bookingOpenAt));
        return fetched;
    }

    private Optional<LocalDateTime> fetchBookingOpenAt(Long showtimeId) {
        try {
            ShowtimeResponse showtime = restClient.get()
                    .uri("/api/showtimes/{showtimeId}", showtimeId)
                    .retrieve()
                    .body(ShowtimeResponse.class);
            return Optional.ofNullable(showtime).map(ShowtimeResponse::bookingOpenAt);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private record ShowtimeResponse(Long id, LocalDateTime bookingOpenAt) {
    }
}
