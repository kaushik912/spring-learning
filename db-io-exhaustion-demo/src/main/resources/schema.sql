CREATE TABLE customer_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    notes VARCHAR(500)
);

CREATE INDEX idx_customer_orders_email ON customer_orders (customer_email);

CREATE ALIAS SLOW_EMAIL_MATCH FOR "com.example.dbioexhaustiondemo.buggy.SlowEmailMatcher.matches";
