package com.pratham.paymentx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // Give the scheduler 5 concurrent threads
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("cron-worker-");
        scheduler.initialize();
        return scheduler;
    }
}