-- Align corporate registration with the legacy customer company-name field.
-- Widening VARCHAR and adding an optional field preserve all existing rows.
ALTER TABLE customer
    ALTER COLUMN cust_companyname TYPE VARCHAR(150),
    ADD COLUMN IF NOT EXISTS company_registration_number VARCHAR(30);

-- Migration 013 in earlier versions added company_name instead of reusing
-- cust_companyname. Copy those values only where the legacy field is blank;
-- retain the old column so no historical values are discarded.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'customer'
          AND column_name = 'company_name'
    ) THEN
        EXECUTE 'UPDATE customer
                 SET cust_companyname = company_name
                 WHERE (cust_companyname IS NULL OR BTRIM(cust_companyname) = '''')
                   AND company_name IS NOT NULL';
    END IF;
END $$;
