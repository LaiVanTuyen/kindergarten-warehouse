package com.kindergarten.warehouse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // Basic setup: Enables Spring's @Async annotation and background task execution
}
