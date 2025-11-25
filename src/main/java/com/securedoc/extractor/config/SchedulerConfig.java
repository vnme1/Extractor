package com.securedoc.extractor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 및 비동기 처리 설정
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulerConfig {
}
