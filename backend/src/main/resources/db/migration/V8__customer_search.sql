-- V8: incremental customer search by name or phone fragment (Phase 3.1, FR-CUS-002).
--
-- search_name is the full name in lower case, without diacritics and with single spaces; the
-- application computes it (SearchText.normalize) on every insert/update. The backfill below
-- covers the Spanish accented letters for rows created before this version.
ALTER TABLE customers ADD COLUMN search_name VARCHAR(160);

UPDATE customers
SET search_name = btrim(regexp_replace(lower(translate(full_name,
        'ÁÀÂÄÉÈÊËÍÌÎÏÓÒÔÖÚÙÛÜÑÇáàâäéèêëíìîïóòôöúùûüñç',
        'AAAAEEEEIIIIOOOOUUUUNCaaaaeeeeiiiioooouuuunc')), '\s+', ' ', 'g'));

ALTER TABLE customers ALTER COLUMN search_name SET NOT NULL;
ALTER TABLE customers ADD CONSTRAINT ck_customers_search_name_lower CHECK (search_name = lower(search_name));

-- "Contains" searches (LIKE '%perez%', LIKE '%8877%') cannot use a B-tree index; trigram GIN
-- indexes serve them for any fragment of 3+ characters. pg_trgm ships with PostgreSQL (contrib)
-- and is a trusted extension, so the database owner can enable it.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_customers_search_name_trgm ON customers USING gin (search_name gin_trgm_ops);
CREATE INDEX ix_customers_phone_trgm ON customers USING gin (phone gin_trgm_ops);
