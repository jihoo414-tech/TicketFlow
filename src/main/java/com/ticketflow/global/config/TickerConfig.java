package com.ticketflow.global.config;

import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TickerConfig {

    @Bean
    public Ticker ticker() {
        return Ticker.systemTicker();
    }
}
