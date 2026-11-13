-- Add customer-supplied booking details without changing existing records.
ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS booking_triptype VARCHAR(30),
    ADD COLUMN IF NOT EXISTS booking_notes VARCHAR(1000);

ALTER TABLE vehicle
    ADD COLUMN IF NOT EXISTS vehicle_luggagecapacity INTEGER;
