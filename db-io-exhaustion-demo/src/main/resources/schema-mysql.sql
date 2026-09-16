DROP TABLE IF EXISTS customer_orders;

CREATE TABLE customer_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    notes VARCHAR(500)
) ENGINE=InnoDB;

CREATE INDEX idx_customer_orders_email ON customer_orders (customer_email);
