-- Match notification storage to the Admin API's 2,000-character limit.
-- Existing messages are retained; stop for review instead of truncating them.
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM notification
        WHERE LENGTH(notification_message) > 2000
    ) THEN
        RAISE EXCEPTION
            'Notification messages longer than 2,000 characters exist; review them before applying migration 017.';
    END IF;
END $$;

ALTER TABLE notification
    ALTER COLUMN notification_message TYPE VARCHAR(2000);

COMMIT;
