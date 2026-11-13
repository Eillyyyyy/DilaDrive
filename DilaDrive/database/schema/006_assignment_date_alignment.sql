-- Align the assignment timestamp column with the API entity and repository.

ALTER TABLE driver_assignment
  ADD COLUMN IF NOT EXISTS assignment_date TIMESTAMP;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'driver_assignment'
      AND column_name = 'assigned_at'
  ) THEN
    UPDATE driver_assignment
    SET assignment_date = assigned_at::timestamp
    WHERE assignment_date IS NULL;
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM driver_assignment
    WHERE assignment_date IS NULL
  ) THEN
    RAISE EXCEPTION
      'Cannot align assignment dates: driver_assignment rows have no assignment_date or legacy assigned_at value';
  END IF;
END $$;

ALTER TABLE driver_assignment
  ALTER COLUMN assignment_date SET DEFAULT CURRENT_TIMESTAMP,
  ALTER COLUMN assignment_date SET NOT NULL;
