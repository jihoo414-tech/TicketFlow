package com.ticketflow.domain.booking.controller;

import com.ticketflow.domain.booking.dto.SeatResponse;
import com.ticketflow.domain.booking.service.HoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final HoldService holdService;

    @GetMapping("/api/showtimes/{showtimeId}/seats")
    public ResponseEntity<List<SeatResponse>> getSeats(@PathVariable Long showtimeId) {
        return ResponseEntity.ok(holdService.getSeats(showtimeId));
    }
}
