CREATE TABLE order_attendee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    price_tier_id BIGINT NOT NULL,
    seat_id BIGINT NULL,
    attendee_name VARCHAR(64) NOT NULL,
    attendee_id_no VARCHAR(64) NOT NULL,
    CONSTRAINT fk_order_attendee_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_attendee_price_tier FOREIGN KEY (price_tier_id) REFERENCES price_tier (id),
    CONSTRAINT fk_order_attendee_seat FOREIGN KEY (seat_id) REFERENCES seat (id),
    INDEX idx_order_attendee_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

ALTER TABLE order_item
    DROP COLUMN attendee_name,
    DROP COLUMN attendee_id_no;
