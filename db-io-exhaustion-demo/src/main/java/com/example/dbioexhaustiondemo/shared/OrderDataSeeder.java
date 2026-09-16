package com.example.dbioexhaustiondemo.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class OrderDataSeeder implements CommandLineRunner {

    // Guaranteed to exist so both the buggy and fixed lookups return a real
    // result, no matter what row count the rest of the table is seeded with.
    public static final String KNOWN_EMAIL = "target.customer@example.com";

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.seed.row-count:1000000}")
    private int rowCount;

    public OrderDataSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        long start = System.currentTimeMillis();
        Random random = new Random(42);
        // Wide filler column so each row scanned costs more than a bare id
        // comparison would - closer to a real orders table than a 2-column one.
        String filler = "x".repeat(400);
        int batchSize = 2000;
        List<Object[]> batch = new ArrayList<>(batchSize);

        String sql = "INSERT INTO customer_orders (customer_email, status, amount, notes) VALUES (?, ?, ?, ?)";

        for (int i = 0; i < rowCount; i++) {
            String email = (i == rowCount / 2) ? KNOWN_EMAIL : "customer" + i + "@example.com";
            batch.add(new Object[]{email, "COMPLETED", BigDecimal.valueOf(random.nextInt(1_000_000), 2), filler});
            if (batch.size() == batchSize) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }

        System.out.printf("Seeded %,d customer_orders rows in %,d ms%n", rowCount, System.currentTimeMillis() - start);
    }
}
