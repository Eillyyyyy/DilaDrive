-- Corporate documents. Customer and booking references use the existing
-- DilaDrive primary keys; application services also verify that they match.
CREATE TABLE IF NOT EXISTS documents (
    document_id BIGSERIAL PRIMARY KEY,
    document_number VARCHAR(20) UNIQUE NOT NULL,
    document_type VARCHAR(20) NOT NULL
        CHECK (document_type IN ('QUOTATION', 'PURCHASE_ORDER', 'INVOICE', 'RECEIPT')),
    booking_id BIGINT NOT NULL REFERENCES booking(booking_id),
    cust_id BIGINT NOT NULL REFERENCES customer(cust_id),
    cust_name VARCHAR(100) NOT NULL,
    cust_email VARCHAR(100),
    cust_phone VARCHAR(20),
    amount DECIMAL(10, 2) NOT NULL CHECK (amount > 0),
    status VARCHAR(50) NOT NULL,
    remarks TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_documents_type_created_at
    ON documents(document_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_documents_booking_id
    ON documents(booking_id);
CREATE INDEX IF NOT EXISTS idx_documents_customer_id
    ON documents(cust_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_row
        WHERE constraint_row.conrelid = 'documents'::regclass
          AND constraint_row.confrelid = 'booking'::regclass
          AND constraint_row.contype = 'f'
          AND cardinality(constraint_row.conkey) = 1
          AND constraint_row.conkey[1] = (
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = 'documents'::regclass
                AND attname = 'booking_id'
                AND NOT attisdropped
          )
    ) THEN
        IF EXISTS (
            SELECT 1
            FROM documents document_row
            LEFT JOIN booking booking_row
                ON booking_row.booking_id = document_row.booking_id
            WHERE booking_row.booking_id IS NULL
        ) THEN
            RAISE EXCEPTION
                'Cannot add documents.booking_id foreign key: orphan document rows exist.';
        END IF;
        ALTER TABLE documents
            ADD CONSTRAINT fk_documents_booking
            FOREIGN KEY (booking_id) REFERENCES booking(booking_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_row
        WHERE constraint_row.conrelid = 'documents'::regclass
          AND constraint_row.confrelid = 'customer'::regclass
          AND constraint_row.contype = 'f'
          AND cardinality(constraint_row.conkey) = 1
          AND constraint_row.conkey[1] = (
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = 'documents'::regclass
                AND attname = 'cust_id'
                AND NOT attisdropped
          )
    ) THEN
        IF EXISTS (
            SELECT 1
            FROM documents document_row
            LEFT JOIN customer customer_row
                ON customer_row.cust_id = document_row.cust_id
            WHERE customer_row.cust_id IS NULL
        ) THEN
            RAISE EXCEPTION
                'Cannot add documents.cust_id foreign key: orphan document rows exist.';
        END IF;
        ALTER TABLE documents
            ADD CONSTRAINT fk_documents_customer
            FOREIGN KEY (cust_id) REFERENCES customer(cust_id);
    END IF;
END
$$;
