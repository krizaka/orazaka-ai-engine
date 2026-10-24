-- What Hibernate's generated schema lacks of the job-service law tables
-- (orazaka-job-service infra/initdb/30-jobs-config.sql). Keep in step with it.

-- At most one open version of an article: the importer closes the old row before it writes the new one.
CREATE UNIQUE INDEX IF NOT EXISTS uq_law_article_version_open
    ON law_article_version (instrument_id, ref, language) WHERE in_force_to IS NULL;

DO $do$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_law_article_version_span') THEN
        ALTER TABLE law_article_version ADD CONSTRAINT ck_law_article_version_span
            CHECK (in_force_to IS NULL OR in_force_to >= in_force_from);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_law_import_manual_traceable') THEN
        ALTER TABLE law_import ADD CONSTRAINT ck_law_import_manual_traceable
            CHECK (mode = 'AUTOMATED' OR (imported_by IS NOT NULL AND dataset_sha256 IS NOT NULL));
    END IF;
END
$do$;
