package com.ticketflow.queue.domain.queue.service;

public sealed interface QueueStatus {

    record Waiting(long position, int retryAfterSeconds) implements QueueStatus {
    }

    record Admitted(EntryPass pass) implements QueueStatus {
    }

    record NotInQueue() implements QueueStatus {
    }
}
