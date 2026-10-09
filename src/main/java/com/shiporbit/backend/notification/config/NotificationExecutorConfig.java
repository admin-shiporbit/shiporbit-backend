package com.shiporbit.backend.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class NotificationExecutorConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    /**
     * Each SMTP send takes ~3-4 s (connect + TLS + auth + DATA), so emails are sent in parallel.
     * Kept small: Gmail throttles too many concurrent connections from one account.
     */
    @Bean(name = NOTIFICATION_EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor(
            @Value("${notification.email.parallelism:4}") int parallelism) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(1_000);
        executor.setThreadNamePrefix("notify-");
        // Queue full: run in the caller's thread instead of dropping the email.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
