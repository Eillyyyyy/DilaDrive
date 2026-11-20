-- Manual forward migration. No booking or assignment rows are modified.
BEGIN;
DO $$
BEGIN
  IF EXISTS (SELECT booking_id FROM driver_assignment
      WHERE upper(assignment_status) NOT IN ('COMPLETED','CANCELLED')
      GROUP BY booking_id HAVING count(*) > 1) THEN
    RAISE EXCEPTION 'Duplicate active assignments need manual review. No records were changed.';
  END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_assignment_active_booking
  ON driver_assignment(booking_id)
  WHERE upper(assignment_status) NOT IN ('COMPLETED','CANCELLED');
COMMIT;
