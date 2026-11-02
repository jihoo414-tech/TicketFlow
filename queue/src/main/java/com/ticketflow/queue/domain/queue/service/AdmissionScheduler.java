package com.ticketflow.queue.domain.queue.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "queue.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AdmissionScheduler {

    private final WaitingQueue waitingQueue;

    @Scheduled(fixedRate = 1000)
    public void admit() {
        waitingQueue.admit();
    }
}
