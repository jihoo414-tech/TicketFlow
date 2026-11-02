package com.ticketflow.queue.domain.queue.controller;

import com.ticketflow.queue.domain.queue.dto.QueueResponse;
import com.ticketflow.queue.domain.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/api/showtimes/{showtimeId}/queue")
    public ResponseEntity<QueueResponse.Status> join(@AuthenticationPrincipal Long memberId,
                                                     @PathVariable Long showtimeId) {
        return ResponseEntity.ok(queueService.join(memberId, showtimeId));
    }

    @GetMapping("/api/showtimes/{showtimeId}/queue/me")
    public ResponseEntity<QueueResponse.Status> status(@AuthenticationPrincipal Long memberId,
                                                       @PathVariable Long showtimeId) {
        return ResponseEntity.ok(queueService.status(memberId, showtimeId));
    }

    @DeleteMapping("/api/showtimes/{showtimeId}/queue/me")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal Long memberId,
                                      @PathVariable Long showtimeId) {
        queueService.leave(memberId, showtimeId);
        return ResponseEntity.noContent().build();
    }
}
