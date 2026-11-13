-- Add recipient metadata for customer-targeted notifications. Existing rows
-- remain unchanged and may keep a NULL recipient until explicitly addressed.
ALTER TABLE notification
    ADD COLUMN IF NOT EXISTS recipient_role VARCHAR(20),
    ADD COLUMN IF NOT EXISTS recipient_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_notification_recipient_created
    ON notification(recipient_role, recipient_id, notification_createdat DESC);
