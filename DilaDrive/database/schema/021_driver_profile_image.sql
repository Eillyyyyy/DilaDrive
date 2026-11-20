-- Additive profile-photo storage; existing rows and relationships are unchanged.
ALTER TABLE driver ADD COLUMN IF NOT EXISTS driver_profile_image TEXT;
