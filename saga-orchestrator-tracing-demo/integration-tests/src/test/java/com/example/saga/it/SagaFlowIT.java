package com.example.saga.it;

import com.example.saga.inventory.InventoryServiceApplication;
import com.example.saga.order.OrderServiceApplication;
import com.example.saga.payment.PaymentServiceApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real Postgres + Kafka (Testcontainers), all three services running in this JVM, talking only over Kafka.
 * No OTel agent here: this verifies the saga logic. Tracing is verified against Jaeger (see README).
 */
class SagaFlowIT {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUsername("saga").withPassword("saga")
            .withCopyFileToContainer(MountableFile.forHostPath("../docker/postgres-init.sql"),
                    "/docker-entrypoint-initdb.d/init.sql");
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    static ConfigurableApplicationContext order, inventory, payment;
    static RestClient http;
    static JdbcTemplate inventoryDb;

    @BeforeAll
    static void start() {
        postgres.start();
        kafka.start();
        order = OrderServiceApplication.start(args("orders", "order"));
        inventory = InventoryServiceApplication.start(args("inventory", "inventory"));
        payment = PaymentServiceApplication.start(args("payment", "payment"));
        int port = ((WebServerApplicationContext) order).getWebServer().getPort();
        http = RestClient.create("http://localhost:" + port);
        inventoryDb = inventory.getBean(JdbcTemplate.class);
    }

    @AfterAll
    static void stop() {
        for (var ctx : List.of(order, inventory, payment)) {
            if (ctx != null) ctx.close();
        }
        kafka.stop();
        postgres.stop();
    }

    private static String[] args(String db, String ignored) {
        String url = "jdbc:postgresql://%s:%d/%s".formatted(postgres.getHost(), postgres.getMappedPort(5432), db);
        return new String[]{
                "--server.port=0",
                "--spring.jmx.enabled=false",
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=saga",
                "--spring.datasource.password=saga",
                "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                "--saga.step-timeout=3s",
                "--saga.retry-base=1s"
        };
    }

    @Test
    void happyPath_completes_andReservesStock() {
        int before = stock("SKU-1");

        String orderId = placeOrder("SKU-1", 2, 100);
        Map<String, Object> saga = awaitTerminal(orderId);

        assertThat(saga.get("state")).isEqualTo("COMPLETED");
        assertThat(states(saga)).containsExactly(
                "STARTED", "INVENTORY_RESERVED", "PAYMENT_AUTHORIZED", "COMPLETED");
        assertThat(stock("SKU-1")).isEqualTo(before - 2);
        assertThat(customerView(orderId)).containsEntry("status", "CONFIRMED").doesNotContainKey("reason");
    }

    @Test
    void paymentFails_releasesInventory_andEndsCompensated() {
        int before = stock("SKU-1");

        String orderId = placeOrder("SKU-1", 1, 1500);
        Map<String, Object> saga = awaitTerminal(orderId);

        assertThat(saga.get("state")).isEqualTo("COMPENSATED");
        assertThat(states(saga)).contains("INVENTORY_RESERVED", "COMPENSATING", "COMPENSATED")
                .doesNotContain("PAYMENT_AUTHORIZED", "COMPLETED");
        assertThat(stock("SKU-1")).as("reserved stock was given back").isEqualTo(before);
        assertThat(customerView(orderId)).containsEntry("status", "FAILED").containsEntry("reason", "PAYMENT_DECLINED");
    }

    @Test
    void outOfStock_compensatesImmediately() {
        String orderId = placeOrder("SKU-2", 1, 100);
        Map<String, Object> saga = awaitTerminal(orderId);

        assertThat(saga.get("state")).isEqualTo("COMPENSATED");
        assertThat(states(saga)).containsExactly("STARTED", "COMPENSATING", "COMPENSATED");
        assertThat(customerView(orderId)).containsEntry("status", "FAILED").containsEntry("reason", "OUT_OF_STOCK");
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private Map<String, Object> customerView(String orderId) {
        return http.get().uri("/orders/{id}", orderId).retrieve().body(Map.class);
    }

    private String placeOrder(String sku, int qty, long amount) {
        Map<?, ?> resp = http.post().uri("/orders").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("sku", sku, "qty", qty, "amount", amount))
                .retrieve().body(Map.class);
        return resp.get("orderId").toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> awaitTerminal(String orderId) {
        Map<String, Object>[] last = new Map[1];
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            last[0] = http.get().uri("/orders/{id}/saga", orderId).retrieve().body(Map.class);
            assertThat(last[0].get("state")).isIn("COMPLETED", "COMPENSATED");
        });
        return last[0];
    }

    @SuppressWarnings("unchecked")
    private static List<String> states(Map<String, Object> saga) {
        return ((List<Map<String, Object>>) saga.get("timeline")).stream()
                .map(m -> (String) m.get("to_state")).distinct().toList();
    }

    private int stock(String sku) {
        return inventoryDb.queryForObject("select available from inventory where sku=?", Integer.class, sku);
    }
}
