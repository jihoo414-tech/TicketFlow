package com.ticketflow.queue.domain.queue.client;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ShowtimeClient {

    Optional<LocalDateTime> findBookingOpenAt(Long showtimeId);
}
