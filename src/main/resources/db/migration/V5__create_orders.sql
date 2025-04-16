CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    performance_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_orders_order_no UNIQUE (order_no),
    CONSTRAINT uk_orders_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_orders_performance FOREIGN KEY (performance_id) REFERENCES performance (id),
    INDEX idx_orders_user_created (user_id, created_at),
    INDEX idx_orders_status_expires (status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    price_tier_id BIGINT NOT NULL,
    seat_id BIGINT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_item_price_tier FOREIGN KEY (price_tier_id) REFERENCES price_tier (id),
    CONSTRAINT fk_order_item_seat FOREIGN KEY (seat_id) REFERENCES seat (id),
    INDEX idx_order_item_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE purchase_quota (
    user_id BIGINT NOT NULL,
    performance_id BIGINT NOT NULL,
    used_quantity INT NOT NULL,
    PRIMARY KEY (user_id, performance_id),
    CONSTRAINT fk_purchase_quota_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_purchase_quota_performance FOREIGN KEY (performance_id) REFERENCES performance (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
