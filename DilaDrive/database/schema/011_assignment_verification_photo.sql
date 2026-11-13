-- Keep the legacy booking/status photo table intact. Current driver uploads
-- are scoped to assignments and use PICKUP/DROP photo types.
CREATE TABLE IF NOT EXISTS assignment_verification_photo (
    photo_id BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES driver_assignment(assignment_id),
    photo_type VARCHAR(20) NOT NULL
        CHECK (photo_type IN ('PICKUP', 'DROP')),
    file_url TEXT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_assignment_verification_photo_assignment
    ON assignment_verification_photo(assignment_id, uploaded_at DESC);
