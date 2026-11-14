package com.example.emailkafka;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT1M")
@ConfigurationPropertiesScan
public class EmailKafkaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailKafkaApplication.class, args);
    }

    @ConfigurationProperties(prefix = "demo")
    public record DemoProperties(String instanceId, int userCount, long providerLatencyMs,
                                 double providerFailureRate, double crashAfterSendRate,
                                 int retries, long retryBackoffMs) {
    }

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime() // all instances use the DB's clock
                .build());
    }
}
