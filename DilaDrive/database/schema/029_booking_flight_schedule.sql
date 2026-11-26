BEGIN;

ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS booking_flight_date DATE,
    ADD COLUMN IF NOT EXISTS booking_flight_time TIME;

COMMIT;