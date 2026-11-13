-- Run once in pgAdmin against the existing DilaDrive database.
ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS cust_profile_image TEXT;
