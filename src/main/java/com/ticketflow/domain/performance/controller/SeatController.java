package com.ticketflow.domain.performance.controller;

import com.ticketflow.domain.performance.dto.SeatResponse;
import com.ticketflow.domain.performance.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/api/showtimes/{showtimeId}/seats")
    public ResponseEntity<List<SeatResponse>> getSeats(@PathVariable Long showtimeId) {
        return ResponseEntity.ok(seatService.getSeats(showtimeId));
    }
}
