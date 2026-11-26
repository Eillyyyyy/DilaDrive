-- Existing vehicle-only holds are retained for audit and ignored by pair availability.
BEGIN;
CREATE TABLE IF NOT EXISTS booking_vehicle_hold (
    booking_id bigint PRIMARY KEY REFERENCES booking(booking_id),
    vehicle_id bigint NOT NULL REFERENCES vehicle(vehicle_id),
    start_at timestamp NOT NULL,
    end_at timestamp NOT NULL,
    expires_at timestamp NOT NULL,
    CHECK (end_at > start_at)
);
ALTER TABLE booking_vehicle_hold
    ADD COLUMN IF NOT EXISTS driver_id bigint REFERENCES driver(driver_id),
    ADD COLUMN IF NOT EXISTS expiry_notified_at timestamp;
CREATE INDEX IF NOT EXISTS ix_booking_vehicle_hold_resource
    ON booking_vehicle_hold(vehicle_id, start_at, end_at);
CREATE INDEX IF NOT EXISTS ix_booking_hold_driver
    ON booking_vehicle_hold(driver_id, start_at, end_at) WHERE driver_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_booking_hold_expiry
    ON booking_vehicle_hold(expires_at) WHERE driver_id IS NOT NULL AND expiry_notified_at IS NULL;
COMMIT;
