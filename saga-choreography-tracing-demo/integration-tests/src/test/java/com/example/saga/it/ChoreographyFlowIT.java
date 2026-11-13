package com.example.saga.it;

import com.example.saga.flowtracker.FlowTrackerApplication;
import com.example.saga.inventory.InventoryServiceApplication;
import com.example.saga.order.OrderServiceApplication;
import com.example.saga.payment.PaymentServiceApplication;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real Postgres + Kafka (Testcontainers), all four services in this JVM, talking only over Kafka. No orchestrator.
 * No OTel agent here: this verifies saga logic + idempotency. Tracing is verified against Jaeger (see README).
 */
class ChoreographyFlowIT {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUsername("saga").withPassword("saga")
            .withCopyFileToContainer(MountableFile.forHostPath("../docker/postgres-init.sql"),
                    "/docker-entrypoint-initdb.d/init.sql");
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    static ConfigurableApplicationContext order, inventory, payment, tracker;
    static RestClient orderApi, trackerApi;
    static JdbcTemplate inventoryDb, orderDb;

    @BeforeAll
    static void start() {
        postgres.start();
        kafka.start();
        order = OrderServiceApplication.start(args("orders"));
        inventory = InventoryServiceApplication.start(args("inventory"));
        payment = PaymentServiceApplication.start(args("payment", "--payment.slow-gateway-delay=6s"));
        tracker = FlowTrackerApplication.start(args("flowtracker"));
        orderApi = RestClient.create("http://localhost:" + port(order));
        trackerApi = RestClient.create("http://localhost:" + port(tracker));
        inventoryDb = inventory.getBean(JdbcTemplate.class);
        orderDb = order.getBean(JdbcTemplate.class);
    }

    @AfterAll
    static void stop() {
        for (var ctx : List.of(order, inventory, payment, tracker)) {
            if (ctx != null) ctx.close();
        }
        kafka.stop();
        postgres.stop();
    }

    private static int port(ConfigurableApplicationContext ctx) {
        return ((WebServerApplicationContext) ctx).getWebServer().getPort();
    }

    private static String[] args(String db, String... extra) {
        String url = "jdbc:postgresql://%s:%d/%s".formatted(postgres.getHost(), postgres.getMappedPort(5432), db);
        String[] base = {
                "--server.port=0",
                "--spring.jmx.enabled=false",
                "--spring.datasource.url=" + url,
                "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                "--saga.outbox.poll-interval-ms=100"
        };
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }

    @Test
    void happyPath_confirmsOrder_andTrackerSeesWholeFlow() {
        int before = stock("SKU-1");

        String orderId = placeOrder("SKU-1", 2, 100);

        awaitStatus(orderId, "CONFIRMED");
        assertThat(stock("SKU-1")).isEqualTo(before - 2);
        awaitFlowStatus(orderId, "CONFIRMED");
        assertThat(flowEvents(orderId)).containsExactly(
                "OrderCreated", "InventoryReserved", "PaymentAuthorized", "OrderConfirmed");
    }

    @Test
    void paymentFails_releasesInventory_andCancelsOrder() {
        int before = stock("SKU-1");

        String orderId = placeOrder("SKU-1", 3, 1500);

        awaitStatus(orderId, "CANCELLED");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stock("SKU-1")).as("reserved stock was given back").isEqualTo(before));
        awaitFlowStatus(orderId, "CANCELLED");
        assertThat(flowEvents(orderId))
                .contains("OrderCreated", "InventoryReserved", "PaymentFailed", "InventoryReleased", "OrderCancelled")
                .doesNotContain("PaymentAuthorized");
    }

    @Test
    void outOfStock_rejectsOrder() {
        String orderId = placeOrder("SKU-2", 1, 100);

        awaitStatus(orderId, "REJECTED");
        awaitFlowStatus(orderId, "REJECTED");
        assertThat(flowEvents(orderId)).containsExactly("OrderCreated", "InventoryRejected", "OrderRejected");
    }

    @Test
    void duplicateDelivery_isHandledOnlyOnce() {
        int before = stock("SKU-1");
        String orderId = placeOrder("SKU-1", 1, 50);
        awaitStatus(orderId, "CONFIRMED");
        assertThat(stock("SKU-1")).isEqualTo(before - 1);

        // Re-deliver the exact same OrderCreated message (same eventId), as Kafka at-least-once might.
        String original = orderDb.queryForObject(
                "select payload from outbox where msg_key = ? and event_type = 'OrderCreated'", String.class, orderId);
        try (var producer = new KafkaProducer<String, String>(producerProps())) {
            producer.send(new ProducerRecord<>("order-events", orderId, original));
            producer.send(new ProducerRecord<>("order-events", orderId, original));
            producer.flush();
        }

        MeterRegistry metrics = inventory.getBean(MeterRegistry.class);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(metrics.counter("events.handled", "service", "inventory-service",
                        "event_type", "OrderCreated", "outcome", "duplicate").count()).isGreaterThanOrEqualTo(2));
        assertThat(stock("SKU-1")).as("stock decremented once only").isEqualTo(before - 1);
        assertThat(inventoryDb.queryForObject("select count(*) from reservation where order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void slowPayment_isReportedStuck_thenConfirms() {
        String orderId = placeOrder("SKU-1", 1, 777);

        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(300)).untilAsserted(() ->
                assertThat(stuckOrderIds(1)).contains(orderId));

        awaitStatus(orderId, "CONFIRMED");
        awaitFlowStatus(orderId, "CONFIRMED");
        assertThat(stuckOrderIds(1)).doesNotContain(orderId);
    }

    // ------------------------------------------------------------------ helpers

    private static Properties producerProps() {
        Properties p = new Properties();
        p.put("bootstrap.servers", kafka.getBootstrapServers());
        p.put("key.serializer", StringSerializer.class.getName());
        p.put("value.serializer", StringSerializer.class.getName());
        return p;
    }

    private String placeOrder(String sku, int qty, long amount) {
        Map<?, ?> resp = orderApi.post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", sku, "qty", qty, "amount", amount))
                .retrieve().body(Map.class);
        return resp.get("orderId").toString();
    }

    private void awaitStatus(String orderId, String expected) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            Map<?, ?> o = orderApi.get().uri("/orders/{id}", orderId).retrieve().body(Map.class);
            assertThat(o.get("status")).isEqualTo(expected);
        });
    }

    private void awaitFlowStatus(String orderId, String expected) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            Map<?, ?> f = trackerApi.get().uri("/flows/{id}", orderId).retrieve().body(Map.class);
            assertThat(f.get("status")).isEqualTo(expected);
        });
    }

    @SuppressWarnings("unchecked")
    private List<String> flowEvents(String orderId) {
        Map<String, Object> f = trackerApi.get().uri("/flows/{id}", orderId).retrieve().body(Map.class);
        return ((List<Map<String, Object>>) f.get("timeline")).stream().map(m -> (String) m.get("eventType")).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> stuckOrderIds(int olderThanSeconds) {
        List<Map<String, Object>> stuck = trackerApi.get()
                .uri("/flows/stuck?olderThanSeconds={s}", olderThanSeconds).retrieve().body(List.class);
        return stuck.stream().map(m -> (String) m.get("orderId")).toList();
    }

    private int stock(String sku) {
        return inventoryDb.queryForObject("select qty from stock where sku = ?", Integer.class, sku);
    }
}
