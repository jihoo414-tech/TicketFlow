package com.ticketflow.queue;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class QueueTestConfiguration {

    @Bean
    @Primary
    TestClock testClock() {
        return new TestClock();
    }

    @Bean
    @Primary
    FakeShowtimeClient fakeShowtimeClient() {
        return new FakeShowtimeClient();
    }
}
