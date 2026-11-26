-- Persist sent fleet expiry reminders so the daily scheduler is idempotent.

CREATE TABLE IF NOT EXISTS fleet_expiry_reminder (
    fleet_expiry_reminder_id BIGSERIAL PRIMARY KEY,
    owner_type VARCHAR(16) NOT NULL CHECK (owner_type IN ('DRIVER', 'VEHICLE')),
    owner_id BIGINT NOT NULL,
    reminder_type VARCHAR(32) NOT NULL
        CHECK (reminder_type IN ('DRIVER_LICENSE', 'VEHICLE_ROAD_TAX', 'VEHICLE_SERVICE')),
    due_date DATE NOT NULL,
    days_before INTEGER NOT NULL CHECK (days_before >= 0),
    notified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_fleet_expiry_reminder UNIQUE
        (owner_type, owner_id, reminder_type, due_date, days_before)
);

CREATE INDEX IF NOT EXISTS idx_fleet_expiry_reminder_due
    ON fleet_expiry_reminder(due_date, reminder_type);
