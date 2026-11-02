package com.ticketflow.queue.domain.queue.controller;

import com.ticketflow.queue.domain.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EntryPassController {

    private final QueueService queueService;

    @PostMapping("/internal/entry-passes/{passId}/release")
    public ResponseEntity<Void> release(@RequestHeader(value = "X-Internal-Secret", required = false) String internalSecret,
                                        @PathVariable String passId) {
        queueService.release(internalSecret, passId);
        return ResponseEntity.noContent().build();
    }
}
