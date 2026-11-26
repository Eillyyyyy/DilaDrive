BEGIN;

ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS preferred_vehicle_id BIGINT REFERENCES vehicle(vehicle_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS preferred_vehicle_category VARCHAR(255);

ALTER TABLE driver_assignment
    ADD COLUMN IF NOT EXISTS previous_assignment_id BIGINT REFERENCES driver_assignment(assignment_id),
    ADD COLUMN IF NOT EXISTS reassignment_reason VARCHAR(500);

CREATE TABLE IF NOT EXISTS booking_pickup_alert (
    booking_id BIGINT NOT NULL REFERENCES booking(booking_id) ON DELETE CASCADE,
    pickup_at TIMESTAMP NOT NULL,
    reason TEXT NOT NULL,
    flagged_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (booking_id, pickup_at)
);

COMMIT;