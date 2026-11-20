-- Option A: additive constraints and reservation end times. No business rows are changed.
BEGIN;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM vehicle WHERE driver_id IS NULL) THEN
    RAISE EXCEPTION 'Unlinked legacy vehicles exist. Resolve them explicitly before applying Option A.';
  END IF;
  IF EXISTS (SELECT driver_id FROM vehicle GROUP BY driver_id HAVING count(*) > 1) THEN
    RAISE EXCEPTION 'Multiple current vehicles exist for a driver. Review the audit; no records have been changed.';
  END IF;
END $$;
ALTER TABLE vehicle ALTER COLUMN driver_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_vehicle_current_driver ON vehicle(driver_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_vehicle_plate_normalized ON vehicle(upper(btrim(vehicle_plateno)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_driver_email_normalized ON driver(lower(btrim(driver_email)));
ALTER TABLE driver_assignment ADD COLUMN IF NOT EXISTS scheduled_end_at TIMESTAMP;

-- A deferred check permits driver + vehicle creation in one transaction.
-- Existing drivers with no vehicle are grandfathered until explicitly repaired.
CREATE OR REPLACE FUNCTION enforce_driver_current_vehicle() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target_id bigint;
BEGIN
  IF TG_TABLE_NAME = 'driver' THEN target_id := NEW.driver_id;
  ELSIF TG_OP = 'DELETE' THEN target_id := OLD.driver_id;
  ELSE target_id := NEW.driver_id;
  END IF;
  IF EXISTS (SELECT 1 FROM driver WHERE driver_id = target_id)
     AND (SELECT count(*) FROM vehicle WHERE driver_id = target_id) <> 1 THEN
    RAISE EXCEPTION 'Driver % must have exactly one current vehicle', target_id USING ERRCODE = '23514';
  END IF;
  IF TG_TABLE_NAME = 'vehicle' AND TG_OP = 'UPDATE' AND OLD.driver_id IS DISTINCT FROM NEW.driver_id THEN
    IF EXISTS (SELECT 1 FROM driver WHERE driver_id = OLD.driver_id)
       AND NOT EXISTS (SELECT 1 FROM vehicle WHERE driver_id = OLD.driver_id) THEN
      RAISE EXCEPTION 'Reassignment would leave driver % without a current vehicle', OLD.driver_id USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NULL;
END $$;
DROP TRIGGER IF EXISTS driver_requires_current_vehicle ON driver;
CREATE CONSTRAINT TRIGGER driver_requires_current_vehicle AFTER INSERT ON driver
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_driver_current_vehicle();
DROP TRIGGER IF EXISTS vehicle_preserves_driver_relationship ON vehicle;
CREATE CONSTRAINT TRIGGER vehicle_preserves_driver_relationship AFTER INSERT OR DELETE OR UPDATE OF driver_id ON vehicle
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_driver_current_vehicle();
COMMIT;
