package com.ticketflow.domain.performance.controller;

import com.ticketflow.domain.admission.service.EntryPasses;
import com.ticketflow.domain.performance.dto.ZoneResponse;
import com.ticketflow.domain.performance.service.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.ticketflow.domain.admission.service.EntryPasses.ENTRY_TOKEN;

@RestController
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;
    private final EntryPasses entryPasses;

    @GetMapping("/api/showtimes/{showtimeId}/zones")
    public ResponseEntity<List<ZoneResponse.Item>> getZones(@AuthenticationPrincipal Long memberId,
                                                           @RequestHeader(value = ENTRY_TOKEN, required = false) String entryToken,
                                                           @PathVariable Long showtimeId) {
        entryPasses.verify(entryToken, memberId, showtimeId);
        return ResponseEntity.ok(zoneService.getZones(showtimeId));
    }
}
