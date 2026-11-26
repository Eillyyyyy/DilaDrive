BEGIN;

ALTER TABLE notification
    ADD COLUMN IF NOT EXISTS entity_type varchar(24),
    ADD COLUMN IF NOT EXISTS entity_id bigint,
    ADD COLUMN IF NOT EXISTS target_route varchar(120),
    ADD COLUMN IF NOT EXISTS read_at timestamp,
    ADD COLUMN IF NOT EXISTS event_key varchar(160);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_recipient_event
    ON notification (recipient_role, recipient_id, event_key);

COMMIT;
