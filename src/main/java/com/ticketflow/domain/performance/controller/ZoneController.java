package com.ticketflow.domain.performance.controller;

import com.ticketflow.domain.performance.dto.ZoneResponse;
import com.ticketflow.domain.performance.service.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @GetMapping("/api/showtimes/{showtimeId}/zones")
    public ResponseEntity<List<ZoneResponse.Item>> getZones(@PathVariable Long showtimeId) {
        return ResponseEntity.ok(zoneService.getZones(showtimeId));
    }
}
