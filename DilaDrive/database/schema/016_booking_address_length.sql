-- Match the booking address storage to the 500-character API validation limit.
-- Existing rows are retained; stop for review instead of truncating long data.
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM booking
        WHERE LENGTH(booking_pickupaddress) > 500
           OR LENGTH(booking_destinationaddress) > 500
    ) THEN
        RAISE EXCEPTION
            'Booking addresses longer than 500 characters exist; review them before applying migration 016.';
    END IF;
END $$;

ALTER TABLE booking
    ALTER COLUMN booking_pickupaddress TYPE VARCHAR(500),
    ALTER COLUMN booking_destinationaddress TYPE VARCHAR(500);

COMMIT;
