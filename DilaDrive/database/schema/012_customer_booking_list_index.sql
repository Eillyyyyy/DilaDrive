-- Support the customer booking list's owner filter and newest-first order.
CREATE INDEX IF NOT EXISTS idx_booking_customer_created_at
    ON booking(cust_id, booking_createdat DESC);
