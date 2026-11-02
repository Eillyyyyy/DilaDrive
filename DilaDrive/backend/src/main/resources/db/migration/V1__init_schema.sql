-- DilaDrive schema v1 (from ERD v2). Enums are varchar + CHECK so JPA @Enumerated(STRING) maps directly.

CREATE TABLE users (
    user_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name       VARCHAR(100) NOT NULL,
    phone_no        VARCHAR(20)  NOT NULL,
    email           VARCHAR(100) NOT NULL UNIQUE,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN','DRIVER','CUSTOMER')),
    account_status  VARCHAR(20)  NOT NULL CHECK (account_status IN ('PENDING','ACTIVE','SUSPENDED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE customers (
    customer_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL UNIQUE REFERENCES users(user_id),
    customer_type       VARCHAR(20) NOT NULL CHECK (customer_type IN ('INDIVIDUAL','CORPORATE')),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED' CHECK (verification_status IN ('UNVERIFIED','VERIFIED','REJECTED')),
    company_name        VARCHAR(150),
    supplier_no         VARCHAR(50),
    contact_person      VARCHAR(100),
    billing_address     TEXT,
    verified_by         BIGINT REFERENCES users(user_id),
    verified_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE drivers (
    driver_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL UNIQUE REFERENCES users(user_id),
    driver_type         VARCHAR(20) NOT NULL CHECK (driver_type IN ('OWNER','ASSOCIATE')),
    license_no          VARCHAR(30) NOT NULL,
    license_expiry      DATE NOT NULL,
    availability_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (availability_status IN ('AVAILABLE','ON_TRIP','OFF_DUTY')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vehicles (
    vehicle_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plate_number      VARCHAR(15) NOT NULL UNIQUE,
    vehicle_model     VARCHAR(80) NOT NULL,
    vehicle_type      VARCHAR(20) NOT NULL CHECK (vehicle_type IN ('SEDAN','MPV','VAN','LIMO')),
    seating_capacity  INT NOT NULL CHECK (seating_capacity > 0),
    luggage_capacity  INT NOT NULL CHECK (luggage_capacity >= 0),
    road_tax_expiry   DATE NOT NULL,
    service_expiry    DATE NOT NULL,
    vehicle_status    VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (vehicle_status IN ('AVAILABLE','IN_USE','MAINTENANCE','INACTIVE')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE zones (
    zone_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE fare_rules (
    fare_rule_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_type           VARCHAR(20) NOT NULL CHECK (rule_type IN ('FIXED_ROUTE','PER_KM')),
    vehicle_type        VARCHAR(20) NOT NULL CHECK (vehicle_type IN ('SEDAN','MPV','VAN','LIMO')),
    origin_zone_id      BIGINT REFERENCES zones(zone_id),
    destination_zone_id BIGINT REFERENCES zones(zone_id),
    fixed_price         NUMERIC(10,2),
    base_fare           NUMERIC(10,2),
    rate_per_km         NUMERIC(10,2),
    min_fare            NUMERIC(10,2),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (rule_type = 'FIXED_ROUTE' AND fixed_price IS NOT NULL AND origin_zone_id IS NOT NULL AND destination_zone_id IS NOT NULL)
        OR
        (rule_type = 'PER_KM' AND base_fare IS NOT NULL AND rate_per_km IS NOT NULL AND min_fare IS NOT NULL)
    )
);

CREATE TABLE system_settings (
    setting_key  VARCHAR(60) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    description  VARCHAR(255)
);

CREATE TABLE bookings (
    booking_id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_no             VARCHAR(20) NOT NULL UNIQUE,
    customer_id            BIGINT NOT NULL REFERENCES customers(customer_id),
    pickup_location        TEXT NOT NULL,
    destination            TEXT NOT NULL,
    pickup_zone_id         BIGINT REFERENCES zones(zone_id),
    destination_zone_id    BIGINT REFERENCES zones(zone_id),
    pickup_datetime        TIMESTAMPTZ NOT NULL,
    passenger_count        INT NOT NULL CHECK (passenger_count > 0),
    luggage_count          INT NOT NULL DEFAULT 0 CHECK (luggage_count >= 0),
    flight_number          VARCHAR(20),
    vehicle_type           VARCHAR(20) NOT NULL CHECK (vehicle_type IN ('SEDAN','MPV','VAN','LIMO')),
    trip_type              VARCHAR(20) NOT NULL DEFAULT 'ONE_WAY' CHECK (trip_type IN ('ONE_WAY','TWO_WAY')),
    po_number              VARCHAR(50),
    special_request        TEXT,
    estimated_distance_km  NUMERIC(8,2),
    estimated_duration_min INT,
    fare_rule_id           BIGINT REFERENCES fare_rules(fare_rule_id),
    calculated_fare        NUMERIC(10,2),
    final_fare             NUMERIC(10,2),
    booking_status         VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (booking_status IN ('PENDING','CONFIRMED','ASSIGNED','IN_PROGRESS','COMPLETED','REJECTED','CANCELLED')),
    payment_status         VARCHAR(20) NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID','PARTIAL','PAID')),
    rejection_reason       TEXT,
    cancel_reason          TEXT,
    verified_by            BIGINT REFERENCES users(user_id),
    verified_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_bookings_customer ON bookings(customer_id);
CREATE INDEX idx_bookings_status_pickup ON bookings(booking_status, pickup_datetime);

CREATE TABLE booking_items (
    item_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id  BIGINT NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    item_type   VARCHAR(20) NOT NULL CHECK (item_type IN ('BASE_FARE','EXTRA_CHARGE','SURCHARGE','DISCOUNT')),
    description VARCHAR(255) NOT NULL,
    quantity    INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price  NUMERIC(10,2) NOT NULL,
    amount      NUMERIC(10,2) NOT NULL
);
CREATE INDEX idx_booking_items_booking ON booking_items(booking_id);

CREATE TABLE trips (
    trip_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id      BIGINT NOT NULL UNIQUE REFERENCES bookings(booking_id),
    driver_id       BIGINT NOT NULL REFERENCES drivers(driver_id),
    vehicle_id      BIGINT NOT NULL REFERENCES vehicles(vehicle_id),
    trip_status     VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED' CHECK (trip_status IN ('ASSIGNED','ARRIVED_PICKUP','PASSENGER_ONBOARD','EN_ROUTE','ARRIVED_DROPOFF','COMPLETED','CANCELLED')),
    assigned_date   TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by     BIGINT REFERENCES users(user_id),
    scheduled_start TIMESTAMPTZ NOT NULL,
    scheduled_end   TIMESTAMPTZ NOT NULL,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (scheduled_end > scheduled_start)
);
CREATE INDEX idx_trips_driver_time  ON trips(driver_id,  scheduled_start, scheduled_end);
CREATE INDEX idx_trips_vehicle_time ON trips(vehicle_id, scheduled_start, scheduled_end);

CREATE TABLE trip_status_history (
    status_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id     BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
    status      VARCHAR(30) NOT NULL,
    update_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  BIGINT REFERENCES users(user_id),
    note        TEXT
);
CREATE INDEX idx_trip_history_trip ON trip_status_history(trip_id, update_time);

CREATE TABLE trip_assignment_log (
    log_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id       BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
    driver_id     BIGINT NOT NULL REFERENCES drivers(driver_id),
    vehicle_id    BIGINT NOT NULL REFERENCES vehicles(vehicle_id),
    assigned_by   BIGINT REFERENCES users(user_id),
    assigned_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    unassigned_at TIMESTAMPTZ,
    reason        TEXT
);

CREATE TABLE payments (
    payment_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id        BIGINT NOT NULL REFERENCES bookings(booking_id),
    amount            NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    payment_method    VARCHAR(20) NOT NULL CHECK (payment_method IN ('CASH','BANK_TRANSFER','WISE','CREDIT_CARD')),
    surcharge_amount  NUMERIC(10,2) NOT NULL DEFAULT 0,
    receipt_file      VARCHAR(255),
    payment_status    VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION' CHECK (payment_status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED')),
    reject_reason     TEXT,
    upload_date       TIMESTAMPTZ NOT NULL DEFAULT now(),
    verification_date TIMESTAMPTZ,
    verified_by       BIGINT REFERENCES users(user_id)
);
CREATE INDEX idx_payments_booking ON payments(booking_id);

CREATE TABLE documents (
    document_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_no     VARCHAR(30) NOT NULL UNIQUE,
    document_type   VARCHAR(20) NOT NULL CHECK (document_type IN ('QUOTATION','INVOICE','RECEIPT')),
    customer_id     BIGINT NOT NULL REFERENCES customers(customer_id),
    payment_id      BIGINT REFERENCES payments(payment_id),
    po_number       VARCHAR(50),
    subtotal        NUMERIC(10,2) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(10,2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(10,2) NOT NULL DEFAULT 0,
    issue_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    due_date        DATE,
    document_status VARCHAR(20) NOT NULL DEFAULT 'ISSUED' CHECK (document_status IN ('DRAFT','ISSUED','VOID')),
    version         INT NOT NULL DEFAULT 1,
    document_file   VARCHAR(255),
    generated_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    generated_by    BIGINT REFERENCES users(user_id)
);
CREATE INDEX idx_documents_customer ON documents(customer_id);

CREATE TABLE document_bookings (
    document_id BIGINT NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    booking_id  BIGINT NOT NULL REFERENCES bookings(booking_id),
    PRIMARY KEY (document_id, booking_id)
);

CREATE TABLE document_items (
    doc_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    booking_id  BIGINT REFERENCES bookings(booking_id),
    description VARCHAR(255) NOT NULL,
    amount      NUMERIC(10,2) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0
);

CREATE TABLE document_sequences (
    document_type VARCHAR(20) PRIMARY KEY CHECK (document_type IN ('QUOTATION','INVOICE','RECEIPT')),
    prefix        VARCHAR(10) NOT NULL,
    last_number   INT NOT NULL DEFAULT 0
);

CREATE TABLE notifications (
    notification_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES users(user_id),
    booking_id           BIGINT REFERENCES bookings(booking_id),
    notification_type    VARCHAR(30) NOT NULL,
    title                VARCHAR(120) NOT NULL,
    notification_message TEXT NOT NULL,
    is_read              BOOLEAN NOT NULL DEFAULT FALSE,
    sent_datetime        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, is_read, sent_datetime DESC);
