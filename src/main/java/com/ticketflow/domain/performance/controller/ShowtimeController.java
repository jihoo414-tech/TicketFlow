package com.ticketflow.domain.performance.controller;

import com.ticketflow.domain.performance.dto.ShowtimeResponse;
import com.ticketflow.domain.performance.service.ShowtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    @GetMapping("/api/performances/{performanceId}/showtimes")
    public ResponseEntity<List<ShowtimeResponse>> getShowtimes(@PathVariable Long performanceId) {
        return ResponseEntity.ok(showtimeService.getShowtimes(performanceId));
    }
}
