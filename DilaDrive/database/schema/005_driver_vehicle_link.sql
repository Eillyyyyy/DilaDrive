-- Associate each fleet vehicle with its registered driver.

ALTER TABLE vehicle
  ADD COLUMN IF NOT EXISTS driver_id BIGINT;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_vehicle_driver'
      AND conrelid = 'vehicle'::regclass
  ) THEN
    ALTER TABLE vehicle
      ADD CONSTRAINT fk_vehicle_driver
      FOREIGN KEY (driver_id)
      REFERENCES driver(driver_id)
      ON DELETE SET NULL;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_vehicle_driver ON vehicle(driver_id);
