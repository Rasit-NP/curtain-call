package com.curtaincall.common.infra;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 운영 코드가 현재 시각을 얻는 UTC 시계. 현재 시각은 이 빈으로만 얻는다(Instant.now() 금지).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
