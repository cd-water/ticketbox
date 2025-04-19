ALTER TABLE order_item
    ADD COLUMN attendee_name VARCHAR(64) NULL,
    ADD COLUMN attendee_id_no VARCHAR(64) NULL;

CREATE TABLE payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    provider VARCHAR(16) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider_txn_no VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_payment_provider_txn UNIQUE (provider_txn_no),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_payment_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ticket (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    performance_id BIGINT NOT NULL,
    price_tier_id BIGINT NOT NULL,
    seat_id BIGINT NULL,
    attendee_name VARCHAR(64) NOT NULL,
    attendee_id_no VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    checked_in_at TIMESTAMP NULL,
    CONSTRAINT uk_ticket_seat UNIQUE (performance_id, seat_id),
    CONSTRAINT fk_ticket_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_ticket_performance FOREIGN KEY (performance_id) REFERENCES performance (id),
    CONSTRAINT fk_ticket_price_tier FOREIGN KEY (price_tier_id) REFERENCES price_tier (id),
    CONSTRAINT fk_ticket_seat FOREIGN KEY (seat_id) REFERENCES seat (id),
    INDEX idx_ticket_performance (performance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
