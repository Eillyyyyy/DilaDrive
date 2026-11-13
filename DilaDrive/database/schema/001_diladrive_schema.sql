-- DilaDrive starter PostgreSQL schema draft.
-- Review and adapt constraints to match the final approved ERD before production use.

CREATE TABLE IF NOT EXISTS admin (
  admin_id BIGSERIAL PRIMARY KEY,
  full_name VARCHAR(120) NOT NULL,
  email VARCHAR(160) UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  admin_role VARCHAR(20) NOT NULL DEFAULT 'ADMIN',
  admin_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  admin_createdat TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  admin_updatedat TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS customer (
  customer_id BIGSERIAL PRIMARY KEY,
  full_name VARCHAR(120) NOT NULL,
  email VARCHAR(160) UNIQUE NOT NULL,
  phone VARCHAR(30),
  password_hash TEXT NOT NULL,
  account_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS driver (
  driver_id BIGSERIAL PRIMARY KEY,
  full_name VARCHAR(120) NOT NULL,
  email VARCHAR(160) UNIQUE NOT NULL,
  phone VARCHAR(30),
  password_hash TEXT NOT NULL,
  account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS vehicle (
  vehicle_id BIGSERIAL PRIMARY KEY,
  registration_no VARCHAR(30) UNIQUE NOT NULL,
  vehicle_type VARCHAR(60) NOT NULL,
  capacity_kg NUMERIC(10,2),
  vehicle_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  driver_id BIGINT,
  CONSTRAINT fk_vehicle_driver
    FOREIGN KEY (driver_id)
    REFERENCES driver(driver_id)
    ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_vehicle_driver ON vehicle(driver_id);

CREATE TABLE IF NOT EXISTS booking (
  booking_id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES customer(customer_id),
  pickup_address TEXT NOT NULL,
  dropoff_address TEXT NOT NULL,
  scheduled_at TIMESTAMPTZ NOT NULL,
  cargo_description TEXT,
  booking_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  estimated_amount NUMERIC(12,2),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS driver_assignment (
  assignment_id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL REFERENCES booking(booking_id),
  driver_id BIGINT NOT NULL REFERENCES driver(driver_id),
  vehicle_id BIGINT NOT NULL REFERENCES vehicle(vehicle_id),
  assignment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  assignment_status VARCHAR(24) NOT NULL DEFAULT 'ASSIGNED'
);

CREATE TABLE IF NOT EXISTS trip_status (
  trip_status_id BIGSERIAL PRIMARY KEY,
  assignment_id BIGINT NOT NULL REFERENCES driver_assignment(assignment_id),
  status VARCHAR(24) NOT NULL,
  notes TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS assignment_verification_photo (
  photo_id BIGSERIAL PRIMARY KEY,
  assignment_id BIGINT NOT NULL REFERENCES driver_assignment(assignment_id),
  photo_type VARCHAR(20) NOT NULL CHECK (photo_type IN ('PICKUP','DROP')),
  file_url TEXT NOT NULL,
  uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payment (
  payment_id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL REFERENCES booking(booking_id),
  amount NUMERIC(12,2) NOT NULL,
  receipt_file_url TEXT,
  payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  submitted_at TIMESTAMPTZ,
  verified_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS quotation (
  quotation_id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES customer(customer_id),
  booking_id BIGINT REFERENCES booking(booking_id),
  quotation_no VARCHAR(40) UNIQUE NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  quotation_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS purchase_order (
  purchase_order_id BIGSERIAL PRIMARY KEY,
  quotation_id BIGINT REFERENCES quotation(quotation_id),
  po_number VARCHAR(40) UNIQUE NOT NULL,
  document_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS invoice (
  invoice_id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL REFERENCES booking(booking_id),
  invoice_no VARCHAR(40) UNIQUE NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  invoice_status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
  issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS receipt (
  receipt_id BIGSERIAL PRIMARY KEY,
  payment_id BIGINT NOT NULL REFERENCES payment(payment_id),
  receipt_no VARCHAR(40) UNIQUE NOT NULL,
  document_url TEXT,
  issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notification (
  notification_id BIGSERIAL PRIMARY KEY,
  recipient_role VARCHAR(20),
  recipient_id BIGINT,
  driver_id BIGINT REFERENCES driver(driver_id),
  notification_title VARCHAR(160) NOT NULL,
  notification_message TEXT NOT NULL,
  notification_type VARCHAR(50),
  notification_status VARCHAR(24) NOT NULL DEFAULT 'UNREAD',
  notification_createdat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_booking_customer ON booking(customer_id);
CREATE INDEX IF NOT EXISTS idx_booking_status ON booking(booking_status);
CREATE INDEX IF NOT EXISTS idx_assignment_driver ON driver_assignment(driver_id);
CREATE INDEX IF NOT EXISTS idx_payment_status ON payment(payment_status);
