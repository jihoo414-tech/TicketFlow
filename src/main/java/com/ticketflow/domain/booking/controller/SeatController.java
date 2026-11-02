package com.ticketflow.domain.booking.controller;

import com.ticketflow.domain.admission.service.EntryPasses;
import com.ticketflow.domain.booking.service.SeatStatusCache;
import com.ticketflow.domain.performance.service.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static com.ticketflow.domain.admission.service.EntryPasses.ENTRY_TOKEN;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatStatusCache seatStatusCache;
    private final ZoneService zoneService;
    private final EntryPasses entryPasses;

    @GetMapping("/api/zones/{zoneId}/seats")
    public ResponseEntity<byte[]> getSeats(@AuthenticationPrincipal Long memberId,
                                           @RequestHeader(value = ENTRY_TOKEN, required = false) String entryToken,
                                           @PathVariable Long zoneId) {
        entryPasses.verify(entryToken, memberId, zoneService.findShowtimeIdOf(zoneId));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(seatStatusCache.getSeats(zoneId));
    }
}
