ALTER TABLE performance
    ADD COLUMN waiting_room_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN admission_rate INT NOT NULL DEFAULT 10,
    ADD COLUMN token_ttl_seconds INT NOT NULL DEFAULT 600;
