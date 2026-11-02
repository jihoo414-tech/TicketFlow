package com.ticketflow.queue.domain.queue.service;

public sealed interface QueueStatus {

    boolean isFor(Long showtimeId);

    record Waiting(Long showtimeId, long position, int retryAfterSeconds) implements QueueStatus {

        @Override
        public boolean isFor(Long showtimeId) {
            return this.showtimeId.equals(showtimeId);
        }
    }

    record Admitted(EntryPass pass) implements QueueStatus {

        @Override
        public boolean isFor(Long showtimeId) {
            return pass.showtimeId().equals(showtimeId);
        }
    }

    record NotInQueue() implements QueueStatus {

        @Override
        public boolean isFor(Long showtimeId) {
            return false;
        }
    }
}
