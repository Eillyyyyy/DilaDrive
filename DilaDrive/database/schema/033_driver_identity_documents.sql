-- Driver identity, vehicle category, ownership, and private fleet documents.
-- Existing driver/vehicle rows remain valid; legacy IC values are encrypted
-- by the application before they are cleared from driver.driver_icno.

ALTER TABLE driver
    ADD COLUMN IF NOT EXISTS driver_icno_ciphertext TEXT,
    ADD COLUMN IF NOT EXISTS driver_icno_lookup CHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_driver_icno_lookup
    ON driver(driver_icno_lookup)
    WHERE driver_icno_lookup IS NOT NULL;

CREATE TABLE IF NOT EXISTS vehicle_category (
    vehicle_category_id BIGSERIAL PRIMARY KEY,
    category_key VARCHAR(40) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL UNIQUE,
    max_passenger_capacity INTEGER NOT NULL CHECK (max_passenger_capacity > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO vehicle_category (category_key, display_name, max_passenger_capacity)
VALUES
    ('SEDAN', 'Sedan', 4),
    ('MPV', 'MPV', 5),
    ('VAN_10_SEATER', '10-Seater Van', 8),
    ('VAN_14_SEATER', '14-Seater Van', 11),
    ('VAN_18_SEATER', '18-Seater Van', 15)
ON CONFLICT (category_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    max_passenger_capacity = EXCLUDED.max_passenger_capacity,
    active = TRUE;

ALTER TABLE vehicle
    ADD COLUMN IF NOT EXISTS vehicle_category_id BIGINT
        REFERENCES vehicle_category(vehicle_category_id),
    ADD COLUMN IF NOT EXISTS vehicle_ownership VARCHAR(20);

UPDATE vehicle v
SET vehicle_category_id = c.vehicle_category_id
FROM vehicle_category c
WHERE v.vehicle_category_id IS NULL
  AND regexp_replace(upper(coalesce(v.vehicle_type, '')), '[^A-Z0-9]', '', 'g')
      = CASE c.category_key
          WHEN 'SEDAN' THEN 'SEDAN'
          WHEN 'MPV' THEN 'MPV'
          WHEN 'VAN_10_SEATER' THEN '10SEATERVAN'
          WHEN 'VAN_14_SEATER' THEN '14SEATERVAN'
          WHEN 'VAN_18_SEATER' THEN '18SEATERVAN'
        END;

CREATE TABLE IF NOT EXISTS fleet_document (
    fleet_document_id BIGSERIAL PRIMARY KEY,
    document_type VARCHAR(24) NOT NULL
        CHECK (document_type IN ('DRIVER_IC', 'DRIVER_LICENSE', 'VEHICLE_ROAD_TAX')),
    driver_id BIGINT REFERENCES driver(driver_id),
    vehicle_id BIGINT REFERENCES vehicle(vehicle_id),
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL
        CHECK (content_type IN ('image/jpeg', 'image/png', 'application/pdf')),
    file_size BIGINT NOT NULL CHECK (file_size > 0 AND file_size <= 5242880),
    storage_key VARCHAR(80) NOT NULL UNIQUE,
    current BOOLEAN NOT NULL DEFAULT TRUE,
    uploaded_by BIGINT NOT NULL REFERENCES admin(admin_id),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    replaced_document_id BIGINT REFERENCES fleet_document(fleet_document_id),
    CONSTRAINT ck_fleet_document_single_owner CHECK (
        (driver_id IS NOT NULL AND vehicle_id IS NULL)
        OR (driver_id IS NULL AND vehicle_id IS NOT NULL)
    ),
    CONSTRAINT ck_fleet_document_owner_type CHECK (
        (document_type IN ('DRIVER_IC', 'DRIVER_LICENSE') AND driver_id IS NOT NULL)
        OR (document_type = 'VEHICLE_ROAD_TAX' AND vehicle_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fleet_document_current_driver_type
    ON fleet_document(driver_id, document_type)
    WHERE current AND driver_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_fleet_document_current_vehicle_type
    ON fleet_document(vehicle_id, document_type)
    WHERE current AND vehicle_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_fleet_document_driver
    ON fleet_document(driver_id, uploaded_at DESC);
CREATE INDEX IF NOT EXISTS idx_fleet_document_vehicle
    ON fleet_document(vehicle_id, uploaded_at DESC);

CREATE TABLE IF NOT EXISTS fleet_document_audit (
    fleet_document_audit_id BIGSERIAL PRIMARY KEY,
    fleet_document_id BIGINT NOT NULL REFERENCES fleet_document(fleet_document_id),
    action VARCHAR(16) NOT NULL
        CHECK (action IN ('UPLOAD', 'REPLACE', 'VIEW', 'REMOVE')),
    admin_id BIGINT NOT NULL REFERENCES admin(admin_id),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_fleet_document_audit_doc_time
    ON fleet_document_audit(fleet_document_id, occurred_at DESC);
