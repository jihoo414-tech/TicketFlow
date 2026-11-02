package com.ticketflow.queue.domain.queue.dto;

import com.ticketflow.queue.domain.queue.service.QueueStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QueueResponse {

    public sealed interface Status permits Waiting, Admitted {
    }

    public record Waiting(String status, long position, int retryAfterSeconds) implements Status {

        public static Waiting from(QueueStatus.Waiting waiting) {
            return new Waiting("WAITING", waiting.position(), waiting.retryAfterSeconds());
        }
    }

    public record Admitted(String status, String entryPass, Instant expiresAt) implements Status {

        public static Admitted of(String entryPass, Instant expiresAt) {
            return new Admitted("ADMITTED", entryPass, expiresAt);
        }
    }
}
