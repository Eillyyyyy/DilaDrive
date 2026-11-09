-- Assignment workflow schema for the existing PostgreSQL DilaDrive database.

CREATE TABLE IF NOT EXISTS driver_assignment (
  assignment_id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL REFERENCES booking(booking_id),
  driver_id BIGINT NOT NULL REFERENCES driver(driver_id),
  vehicle_id BIGINT NOT NULL REFERENCES vehicle(vehicle_id),
  assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  assignment_status VARCHAR(24) NOT NULL DEFAULT 'ASSIGNED'
);

CREATE INDEX IF NOT EXISTS idx_assignment_booking
  ON driver_assignment(booking_id);
CREATE INDEX IF NOT EXISTS idx_assignment_vehicle
  ON driver_assignment(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_assignment_status
  ON driver_assignment(assignment_status);

ALTER TABLE notification
  ADD COLUMN IF NOT EXISTS driver_id BIGINT REFERENCES driver(driver_id);

CREATE INDEX IF NOT EXISTS idx_notification_driver_sent_at
  ON notification(driver_id, sent_at DESC);
