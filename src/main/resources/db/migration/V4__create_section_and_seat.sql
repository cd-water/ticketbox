CREATE TABLE section (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    performance_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    price_tier_id BIGINT NOT NULL,
    row_count INT NOT NULL,
    seats_per_row INT NOT NULL,
    CONSTRAINT fk_section_performance FOREIGN KEY (performance_id) REFERENCES performance (id),
    CONSTRAINT fk_section_price_tier FOREIGN KEY (price_tier_id) REFERENCES price_tier (id),
    CONSTRAINT uk_section_performance_name UNIQUE (performance_id, name),
    INDEX idx_section_performance (performance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE seat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    performance_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    row_no INT NOT NULL,
    seat_no INT NOT NULL,
    CONSTRAINT fk_seat_performance FOREIGN KEY (performance_id) REFERENCES performance (id),
    CONSTRAINT fk_seat_section FOREIGN KEY (section_id) REFERENCES section (id),
    CONSTRAINT uk_seat_position UNIQUE (performance_id, section_id, row_no, seat_no),
    INDEX idx_seat_performance_section (performance_id, section_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
