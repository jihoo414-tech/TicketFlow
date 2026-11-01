package com.ticketflow.domain.booking.controller;

import com.ticketflow.domain.booking.service.SeatStatusCache;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatStatusCache seatStatusCache;

    @GetMapping("/api/showtimes/{showtimeId}/seats")
    public ResponseEntity<byte[]> getSeats(@PathVariable Long showtimeId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(seatStatusCache.getSeats(showtimeId));
    }
}
