BEGIN;

DO $$
DECLARE
    link record;
    source_relation regclass;
    source_column smallint;
    admin_relation regclass := 'admin'::regclass;
    admin_column smallint;
BEGIN
    SELECT attnum INTO STRICT admin_column FROM pg_attribute
    WHERE attrelid = admin_relation AND attname = 'admin_id' AND NOT attisdropped;

    FOR link IN SELECT * FROM (VALUES
        ('payment', 'verified_by', 'fk_payment_verified_by_current'),
        ('payment', 'recorded_by_admin_id', 'fk_payment_recorded_admin_current'),
        ('booking_fare_revision', 'proposed_by', 'fk_booking_fare_revision_proposed_by')
    ) AS links(table_name, column_name, constraint_name)
    LOOP
        source_relation := to_regclass(link.table_name);
        SELECT attnum INTO STRICT source_column FROM pg_attribute
        WHERE attrelid = source_relation AND attname = link.column_name AND NOT attisdropped;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE contype = 'f' AND conrelid = source_relation AND confrelid = admin_relation
              AND conkey = ARRAY[source_column] AND confkey = ARRAY[admin_column] AND convalidated
        ) THEN
            EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %s(admin_id) ON DELETE SET NULL',
                source_relation, link.constraint_name, link.column_name, admin_relation);
        END IF;
    END LOOP;
END $$;

COMMIT;
