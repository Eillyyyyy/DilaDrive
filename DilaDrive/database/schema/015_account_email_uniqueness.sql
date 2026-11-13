-- Enforce the case-insensitive email uniqueness expected by each login role.
-- Do not rewrite or delete existing accounts. Abort clearly if legacy rows
-- need review before the indexes can be installed.
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM customer
        WHERE cust_email IS NOT NULL AND BTRIM(cust_email) <> ''
        GROUP BY LOWER(BTRIM(cust_email))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate customer emails exist; review them before applying migration 015.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM driver
        WHERE driver_email IS NOT NULL AND BTRIM(driver_email) <> ''
        GROUP BY LOWER(BTRIM(driver_email))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate driver emails exist; review them before applying migration 015.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM admin
        WHERE admin_email IS NOT NULL AND BTRIM(admin_email) <> ''
        GROUP BY LOWER(BTRIM(admin_email))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate admin emails exist; review them before applying migration 015.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_email_normalized
    ON customer (LOWER(BTRIM(cust_email)))
    WHERE cust_email IS NOT NULL AND BTRIM(cust_email) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_driver_email_normalized
    ON driver (LOWER(BTRIM(driver_email)))
    WHERE driver_email IS NOT NULL AND BTRIM(driver_email) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_admin_email_normalized
    ON admin (LOWER(BTRIM(admin_email)))
    WHERE admin_email IS NOT NULL AND BTRIM(admin_email) <> '';

COMMIT;
