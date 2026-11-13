ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS source_document_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_row
        WHERE constraint_row.conrelid = 'documents'::regclass
          AND constraint_row.conname = 'fk_documents_source_document'
    ) THEN
        ALTER TABLE documents
            ADD CONSTRAINT fk_documents_source_document
            FOREIGN KEY (source_document_id)
            REFERENCES documents(document_id)
            ON DELETE RESTRICT;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_documents_source_document_id
    ON documents(source_document_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_documents_type_source_document
    ON documents(document_type, source_document_id)
    WHERE source_document_id IS NOT NULL;
