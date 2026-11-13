-- Makes customer booking retries safe without changing existing booking rows.
-- created_at represents an absolute creation instant. Existing databases that
-- ran an earlier version of this migration already use TIMESTAMPTZ.
CREATE TABLE IF NOT EXISTS booking_request_idempotency (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customer(cust_id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    booking_id BIGINT,
    response_status VARCHAR(24),
    -- BookingRequestIdempotency.createdAt is a Java Instant.
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_booking_request_idempotency UNIQUE (customer_id, idempotency_key)
);

-- Keep the physical type aligned with BookingRequestIdempotency.requestHash.
-- rtrim preserves the SHA-256 value if this migration previously created CHAR(64).
ALTER TABLE booking_request_idempotency
    ALTER COLUMN request_hash TYPE VARCHAR(64)
    USING rtrim(request_hash)::VARCHAR(64);

-- The row is inserted before the booking is saved, then linked before commit.
ALTER TABLE booking_request_idempotency
    ALTER COLUMN booking_id DROP NOT NULL;

