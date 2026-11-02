package com.ticketflow.queue.global.config;

import com.ticketflow.queue.domain.queue.service.WaitingQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class QueueConfig {

    @Bean
    public WaitingQueue waitingQueue(Clock clock,
                                     @Value("${queue.admission-limit}") int admissionLimit,
                                     @Value("${queue.admits-per-second}") int admitsPerSecond) {
        return new WaitingQueue(clock, admissionLimit, admitsPerSecond);
    }
}
