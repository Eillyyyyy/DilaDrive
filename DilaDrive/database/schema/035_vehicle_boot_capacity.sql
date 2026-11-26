BEGIN;

ALTER TABLE vehicle_category ADD COLUMN boot_capacity_units INTEGER;

INSERT INTO vehicle_category (category_key, display_name, max_passenger_capacity, boot_capacity_units)
VALUES
    ('SEDAN', 'Sedan', 4, 6),
    ('MPV', 'MPV', 6, 12),
    ('PREMIUM_MPV', 'Premium MPV', 6, 15),
    ('VAN_10_SEATER', '10-Seater Van', 10, 18),
    ('VAN_14_SEATER', '14-Seater Van', 11, 21),
    ('VAN_18_SEATER', '18-Seater Van', 15, 24)
ON CONFLICT (category_key) DO UPDATE
SET max_passenger_capacity = EXCLUDED.max_passenger_capacity,
    boot_capacity_units = EXCLUDED.boot_capacity_units;

-- Link older vehicles by category name or key when no relationship was recorded.
UPDATE vehicle v
SET vehicle_category_id = c.vehicle_category_id
FROM vehicle_category c
WHERE v.vehicle_category_id IS NULL
  AND CASE regexp_replace(upper(v.vehicle_type), '[^A-Z0-9]', '', 'g')
      WHEN 'VAN10' THEN 'VAN10SEATER'
      WHEN 'VAN14' THEN 'VAN14SEATER'
      WHEN 'VAN18' THEN 'VAN18SEATER'
      WHEN '10SEATER' THEN 'VAN10SEATER'
      WHEN '14SEATER' THEN 'VAN14SEATER'
      WHEN '18SEATER' THEN 'VAN18SEATER'
      ELSE regexp_replace(upper(v.vehicle_type), '[^A-Z0-9]', '', 'g')
  END IN (
      regexp_replace(upper(c.category_key), '[^A-Z0-9]', '', 'g'),
      regexp_replace(upper(c.display_name), '[^A-Z0-9]', '', 'g'));

-- Use the smallest recorded boot allowance for any additional fleet category.
UPDATE vehicle_category c
SET boot_capacity_units = COALESCE((
    SELECT MIN(v.vehicle_luggagecapacity)
    FROM vehicle v
    WHERE v.vehicle_category_id = c.vehicle_category_id AND v.vehicle_luggagecapacity >= 0
), 0)
WHERE c.boot_capacity_units IS NULL;

ALTER TABLE vehicle_category
    ALTER COLUMN boot_capacity_units SET NOT NULL,
    ADD CONSTRAINT vehicle_category_boot_capacity_check CHECK (boot_capacity_units >= 0);

-- Keep the existing vehicle columns synchronized for legacy API consumers.
UPDATE vehicle v
SET vehicle_capacity = c.max_passenger_capacity,
    vehicle_luggagecapacity = c.boot_capacity_units
FROM vehicle_category c
WHERE v.vehicle_category_id = c.vehicle_category_id;

COMMIT;
