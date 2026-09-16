package com.example.ioexhaustiondemo.fixed;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated, bounded pool for blocking downstream calls - isolated from
 * Tomcat's own request-handling pool (the bulkhead pattern) so a slow
 * downstream call can never starve the web tier. Sized larger than Tomcat's
 * own pool on purpose: threads sitting here are blocked waiting on I/O, not
 * consuming CPU, so it's cheap to size an I/O-bound pool well above a
 * CPU-bound request-handling pool.
 */
@Configuration
@EnableAsync
public class DownstreamExecutorConfig {

    @Bean(name = "downstreamExecutor")
    public Executor downstreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("downstream-exec-");
        executor.initialize();
        return executor;
    }
}
