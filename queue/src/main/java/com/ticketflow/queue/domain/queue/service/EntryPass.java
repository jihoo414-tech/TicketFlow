package com.ticketflow.queue.domain.queue.service;

import java.time.Instant;

public record EntryPass(
        String id,
        Long memberId,
        Long showtimeId,
        Instant expiresAt
) {
}
