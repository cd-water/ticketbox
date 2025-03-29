CREATE TABLE performance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    venue_name VARCHAR(120) NOT NULL,
    venue_address VARCHAR(200) NOT NULL,
    sale_start_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_performance_event FOREIGN KEY (event_id) REFERENCES event (id),
    INDEX idx_performance_event_starts (event_id, starts_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE price_tier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    performance_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_price_tier_performance FOREIGN KEY (performance_id) REFERENCES performance (id),
    INDEX idx_price_tier_performance (performance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
