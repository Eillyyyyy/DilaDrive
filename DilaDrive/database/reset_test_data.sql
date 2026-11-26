-- psql script for the confirmed local DilaDrive reset only.
\set ON_ERROR_STOP on
\if :{?reset_confirmed}
\else
  \echo 'Reset skipped. Explicit confirmation is required.'
  \quit
\endif
\if :reset_confirmed
\else
  \echo 'Reset skipped. Explicit confirmation is required.'
  \quit
\endif

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL search_path = pg_catalog, public, pg_temp;
LOCK TABLE public.admin, public.assignment, public.assignment_verification_photo, public.booking, public.booking_fare_rate, public.booking_fare_revision, public.booking_pickup_alert, public.booking_request_idempotency, public.booking_vehicle_hold, public.customer, public.customer_verification_history, public.documents, public.driver, public.driver_assignment, public.driver_commission_audit, public.driver_trip_commission, public.driver_unavailable_period, public.invoice, public.mobile_push_token, public.notification, public.payment, public.purchase_order, public.quotation, public.receipt, public.trip_charge_revision, public.trip_incident, public.trip_milestone_event, public.trip_pricing_settings, public.trip_status, public.vehicle, public.vehicle_unavailable_period, public.verification_photo IN ACCESS EXCLUSIVE MODE;

DO $reset$
DECLARE
    expected jsonb := '{"trip_pricing_settings":1,"vehicle_unavailable_period":0,"mobile_push_token":1,"driver_unavailable_period":0,"customer_verification_history":0,"booking_fare_rate":0,"booking":10,"booking_vehicle_hold":0,"booking_fare_revision":0,"assignment_verification_photo":2,"driver_trip_commission":1,"driver_commission_audit":0,"booking_pickup_alert":0,"trip_charge_revision":0,"payment":2,"documents":7,"admin":2,"assignment":1,"driver_assignment":5,"vehicle":5,"customer":4,"trip_status":2,"verification_photo":2,"driver":6,"quotation":1,"purchase_order":1,"invoice":2,"booking_request_idempotency":3,"receipt":1,"notification":27,"trip_milestone_event":1,"trip_incident":0}';
    retained text[] := ARRAY['admin','booking_fare_rate','trip_pricing_settings'];
    preserved jsonb := '{}'::jsonb;
    item record;
    seq record;
    actual bigint;
    fingerprint text;
BEGIN
    IF current_database() <> 'DilaDrive'
        OR inet_server_addr() IS DISTINCT FROM '127.0.0.1'::inet
        OR inet_server_port() IS DISTINCT FROM 5432
        OR pg_is_in_recovery()
        OR current_setting('data_directory') <> 'C:/Program Files/PostgreSQL/18/data'
        OR (SELECT oid FROM pg_database WHERE datname=current_database()) <> 24580 THEN
        RAISE EXCEPTION 'Database does not match the audited local reset target';
    END IF;

    IF (SELECT array_agg(tablename::text ORDER BY tablename::text)
        FROM pg_tables WHERE schemaname='public')
        IS DISTINCT FROM (SELECT array_agg(key ORDER BY key) FROM jsonb_object_keys(expected) AS k(key))
        OR EXISTS (SELECT 1 FROM pg_tables
                   WHERE schemaname NOT IN ('public','pg_catalog','information_schema')) THEN
        RAISE EXCEPTION 'Table inventory changed. Re-audit before resetting';
    END IF;

    IF (SELECT array_agg(admin_id::text || ':' || admin_role || ':' || admin_status ORDER BY admin_id)
        FROM public.admin) IS DISTINCT FROM ARRAY['1:SUPER_ADMIN:ACTIVE','2:ADMIN:ACTIVE'] THEN
        RAISE EXCEPTION 'Admin inventory changed';
    END IF;

    FOR item IN SELECT * FROM jsonb_each_text(expected) LOOP
        EXECUTE format('SELECT count(*) FROM public.%I', item.key) INTO actual;
        IF actual <> item.value::bigint THEN
            RAISE EXCEPTION 'Row count changed for %. Expected %, got %', item.key, item.value, actual;
        END IF;
        IF item.key = ANY(retained) THEN
            EXECUTE format('SELECT md5(coalesce(string_agg(to_jsonb(t)::text, '''' ORDER BY to_jsonb(t)::text), '''')) FROM public.%I t', item.key)
                INTO fingerprint;
            preserved := preserved || jsonb_build_object(item.key, fingerprint);
        END IF;
    END LOOP;

    DELETE FROM public.trip_incident;
    DELETE FROM public.trip_milestone_event;
    DELETE FROM public.assignment_verification_photo;
    DELETE FROM public.verification_photo;
    DELETE FROM public.driver_commission_audit;
    DELETE FROM public.driver_trip_commission;
    DELETE FROM public.trip_charge_revision;
    DELETE FROM public.receipt;
    DELETE FROM public.documents;
    DELETE FROM public.invoice;
    DELETE FROM public.purchase_order;
    DELETE FROM public.quotation;
    DELETE FROM public.payment;
    DELETE FROM public.notification;
    DELETE FROM public.mobile_push_token;
    DELETE FROM public.booking_request_idempotency;
    DELETE FROM public.customer_verification_history;
    DELETE FROM public.booking_pickup_alert;
    DELETE FROM public.booking_fare_revision;
    DELETE FROM public.booking_vehicle_hold;
    DELETE FROM public.driver_unavailable_period;
    DELETE FROM public.vehicle_unavailable_period;
    DELETE FROM public.trip_status;
    DELETE FROM public.assignment;
    DELETE FROM public.driver_assignment;
    DELETE FROM public.booking;
    DELETE FROM public.vehicle;
    DELETE FROM public.driver;
    DELETE FROM public.customer;

    -- Force deferred FK/constraint-trigger checks before restarting sequences.
    SET CONSTRAINTS ALL IMMEDIATE;
    FOR item IN SELECT * FROM jsonb_each_text(expected) LOOP
        IF item.key = ANY(retained) THEN
            EXECUTE format('SELECT md5(coalesce(string_agg(to_jsonb(t)::text, '''' ORDER BY to_jsonb(t)::text), '''')) FROM public.%I t', item.key)
                INTO fingerprint;
            IF fingerprint IS DISTINCT FROM preserved ->> item.key THEN
                RAISE EXCEPTION 'Retained records changed in %', item.key;
            END IF;
        ELSE
            EXECUTE format('SELECT count(*) FROM public.%I', item.key) INTO actual;
            IF actual <> 0 THEN RAISE EXCEPTION '% still holds rows', item.key; END IF;
        END IF;
    END LOOP;

    -- RESTART changes only sequence state and is rolled back with this transaction.
    FOR seq IN
        SELECT sn.nspname, s.relname, p.seqstart
        FROM pg_depend d
        JOIN pg_class s ON s.oid=d.objid AND s.relkind='S'
        JOIN pg_namespace sn ON sn.oid=s.relnamespace
        JOIN pg_class a ON a.oid=d.refobjid
        JOIN pg_namespace an ON an.oid=a.relnamespace
        JOIN pg_sequence p ON p.seqrelid=s.oid
        WHERE d.classid='pg_class'::regclass AND d.refclassid='pg_class'::regclass
          AND d.deptype IN ('a','i') AND an.nspname='public'
          AND expected ? a.relname::text AND NOT (a.relname::text = ANY(retained))
          AND a.relname NOT IN ('customer','driver')
    LOOP
        EXECUTE format('ALTER SEQUENCE %I.%I RESTART WITH %s', seq.nspname, seq.relname, seq.seqstart);
    END LOOP;
END
$reset$;

COMMIT;
