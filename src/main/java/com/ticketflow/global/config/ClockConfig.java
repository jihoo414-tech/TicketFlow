package com.ticketflow.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

// DB의 DATETIME 값은 한국 시간으로 저장하므로, 서버 OS의 타임존과 상관없이 같은 기준으로 비교하도록 고정한다.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
