\set ON_ERROR_STOP on
\if :{?apply}
\else
\set apply false
\endif
BEGIN;

DO $$
DECLARE
    admins bigint[];
    customers bigint[];
    drivers bigint[];
    vehicles bigint[];
    bookings bigint[];
    deleted_count integer;
BEGIN
    IF current_database() <> 'DilaDrive'
       OR inet_server_port() IS DISTINCT FROM 5432
       OR inet_server_addr() IS NULL
       OR inet_server_addr() NOT IN ('127.0.0.1'::inet, '::1'::inet) THEN
        RAISE EXCEPTION 'Refusing cleanup outside local dev';
    END IF;

    SELECT ARRAY(SELECT admin_id FROM public.admin
        WHERE admin_email='hardening-admin@example.invalid' AND admin_name='DEV-SEED Admin' FOR UPDATE) INTO admins;
    SELECT ARRAY(SELECT cust_id FROM public.customer WHERE (cust_email,cust_name) IN (
        ('hardening-normal@example.invalid','DEV-SEED Customer'),
        ('hardening-company@example.invalid','DEV-SEED Company'),
        ('hardening-pending@example.invalid','DEV-SEED Pending')) FOR UPDATE) INTO customers;
    SELECT ARRAY(SELECT driver_id FROM public.driver WHERE (driver_email,driver_name) IN (
        ('hardening-driver1@example.invalid','DEV-SEED Driver One'),
        ('hardening-driver2@example.invalid','DEV-SEED Driver Two')) FOR UPDATE) INTO drivers;
    SELECT ARRAY(SELECT v.vehicle_id FROM public.vehicle v JOIN public.driver d USING(driver_id)
        WHERE v.driver_id=ANY(drivers) AND v.vehicle_model='DEV-SEED Sedan'
          AND (d.driver_email,v.vehicle_plateno) IN (('hardening-driver1@example.invalid','FIXTURE1'),
              ('hardening-driver2@example.invalid','FIXTURE2')) FOR UPDATE OF v) INTO vehicles;
    SELECT ARRAY(SELECT b.booking_id FROM public.booking b JOIN public.customer c USING(cust_id)
        WHERE b.cust_id=ANY(customers) AND b.booking_destinationaddress='DEV-SEED Destination'
          AND (c.cust_email,b.booking_pickupaddress) IN (
              ('hardening-normal@example.invalid','DEV-SEED Pickup A'),
              ('hardening-company@example.invalid','DEV-SEED Pickup B'),
              ('hardening-normal@example.invalid','DEV-SEED Pending Pickup')) FOR UPDATE OF b) INTO bookings;

    -- Declared CASCADE/SET NULL references must never modify rows outside this fixture.
    IF EXISTS (SELECT 1 FROM public.booking_pickup_alert WHERE booking_id=ANY(bookings))
       OR EXISTS (SELECT 1 FROM public.payment WHERE recorded_by_driver_id=ANY(drivers)
                  OR verified_by=ANY(admins) OR recorded_by_admin_id=ANY(admins))
       OR EXISTS (SELECT 1 FROM public.booking_fare_revision WHERE proposed_by=ANY(admins))
       OR EXISTS (SELECT 1 FROM public.booking WHERE preferred_vehicle_id=ANY(vehicles)
                  AND NOT (booking_id=ANY(bookings)))
       OR EXISTS (SELECT 1 FROM public.notification WHERE
                  (recipient_role='CUSTOMER' AND recipient_id=ANY(customers))
                  OR (recipient_role='DRIVER' AND recipient_id=ANY(drivers))
                  OR (recipient_role='ADMIN' AND recipient_id=ANY(admins)))
       OR EXISTS (SELECT 1 FROM public.mobile_push_token WHERE
                  (recipient_role='CUSTOMER' AND recipient_id=ANY(customers))
                  OR (recipient_role='DRIVER' AND recipient_id=ANY(drivers))) THEN
        RAISE EXCEPTION 'Dependent rows exist outside the original seed. Cleanup rolled back.';
    END IF;

    DELETE FROM public.booking_vehicle_hold h USING public.booking b, public.vehicle v
    WHERE h.booking_id=b.booking_id AND h.vehicle_id=v.vehicle_id AND h.driver_id=v.driver_id
      AND b.booking_id=ANY(bookings) AND b.booking_pickupaddress='DEV-SEED Pending Pickup'
      AND v.vehicle_id=ANY(vehicles) AND v.vehicle_plateno='FIXTURE1';
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RAISE NOTICE 'Fixture holds: %', deleted_count;

    DELETE FROM public.driver_assignment a USING public.booking b, public.vehicle v
    WHERE a.booking_id=b.booking_id AND a.vehicle_id=v.vehicle_id AND a.driver_id=v.driver_id
      AND a.previous_assignment_id IS NULL AND a.assignment_status='ASSIGNED'
      AND b.booking_id=ANY(bookings) AND v.vehicle_id=ANY(vehicles)
      AND (b.booking_pickupaddress,v.vehicle_plateno) IN (('DEV-SEED Pickup A','FIXTURE1'),('DEV-SEED Pickup B','FIXTURE2'));
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RAISE NOTICE 'Fixture assignments: %', deleted_count;

    DELETE FROM public.booking WHERE booking_id=ANY(bookings);
    DELETE FROM public.vehicle WHERE vehicle_id=ANY(vehicles);
    DELETE FROM public.driver WHERE driver_id=ANY(drivers);
    DELETE FROM public.customer WHERE cust_id=ANY(customers);
    DELETE FROM public.admin WHERE admin_id=ANY(admins);
    RAISE NOTICE 'Fixture bookings: %; vehicles: %; drivers: %; customers: %; admins: %',
        cardinality(bookings), cardinality(vehicles), cardinality(drivers), cardinality(customers), cardinality(admins);
END $$;
SET CONSTRAINTS ALL IMMEDIATE;
\if :apply
COMMIT;
\else
ROLLBACK;
\echo 'Dry run complete. No rows were deleted.'
\endif
