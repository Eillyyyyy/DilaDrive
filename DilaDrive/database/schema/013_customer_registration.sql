-- Store corporate registration details while reusing the legacy company-name
-- field so existing customer names remain visible to the current application.
ALTER TABLE customer
  ALTER COLUMN cust_companyname TYPE VARCHAR(150),
  ADD COLUMN IF NOT EXISTS company_registration_number VARCHAR(30);
