-- V12: product cost and sale price, pricing snapshots of spare parts used in repairs, and the
-- configurable public home-service portal (BF-BR-001 v1.6, BF-ARCH-001 ADR-019).
--
-- Money follows repair_quotes.amount: NUMERIC(12,2) in colones (exact decimals, never float,
-- DATA-006). NULL means "not known yet"; amounts are never negative.

-- ---- products: cost and sale price ----
-- The catalog stays global: stock is still per branch (branch_stock); no quantity column here.
ALTER TABLE products ADD COLUMN unit_cost NUMERIC(12, 2);
ALTER TABLE products ADD COLUMN sale_price NUMERIC(12, 2);
-- Default decision when the part is used in a repair; each part line can decide otherwise.
ALTER TABLE products ADD COLUMN chargeable_by_default BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE products ADD CONSTRAINT ck_products_unit_cost CHECK (unit_cost IS NULL OR unit_cost >= 0);
ALTER TABLE products ADD CONSTRAINT ck_products_sale_price CHECK (sale_price IS NULL OR sale_price >= 0);

-- ---- repair_part_usages: what the part cost and what is charged, frozen when it is used ----
-- Prices are never recomputed from the current catalog: a later price change affects only new lines.
-- Lines recorded before V12 have no price (NULL) and are treated as chargeable with the price to be
-- defined; adding the columns does not update any row, so their history stays untouched.
ALTER TABLE repair_part_usages ADD COLUMN unit_price NUMERIC(12, 2);
ALTER TABLE repair_part_usages ADD COLUMN unit_cost NUMERIC(12, 2);
ALTER TABLE repair_part_usages ADD COLUMN chargeable BOOLEAN NOT NULL DEFAULT TRUE;
-- TRUE when the price was set for this line instead of taken from the catalog.
ALTER TABLE repair_part_usages ADD COLUMN price_overridden BOOLEAN NOT NULL DEFAULT FALSE;
-- New lines always state both decisions explicitly.
ALTER TABLE repair_part_usages ALTER COLUMN chargeable DROP DEFAULT;
ALTER TABLE repair_part_usages ALTER COLUMN price_overridden DROP DEFAULT;
ALTER TABLE repair_part_usages ADD CONSTRAINT ck_repair_part_usages_unit_price
    CHECK (unit_price IS NULL OR unit_price >= 0);
ALTER TABLE repair_part_usages ADD CONSTRAINT ck_repair_part_usages_unit_cost
    CHECK (unit_cost IS NULL OR unit_cost >= 0);
ALTER TABLE repair_part_usages ADD CONSTRAINT ck_repair_part_usages_override
    CHECK (NOT price_overridden OR unit_price IS NOT NULL);

-- The pricing snapshot is history too: only the running return total may still grow (V10 rule).
CREATE OR REPLACE FUNCTION guard_repair_part_usage() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Repair part usages cannot be deleted' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF NEW.order_id <> OLD.order_id OR NEW.product_id <> OLD.product_id OR NEW.branch_id <> OLD.branch_id
        OR NEW.quantity <> OLD.quantity OR NEW.operation_id <> OLD.operation_id
        OR NEW.request_fingerprint <> OLD.request_fingerprint OR NEW.movement_id <> OLD.movement_id
        OR NEW.note IS DISTINCT FROM OLD.note OR NEW.recorded_by <> OLD.recorded_by
        OR NEW.recorded_at <> OLD.recorded_at OR NEW.returned_quantity < OLD.returned_quantity
        OR NEW.unit_price IS DISTINCT FROM OLD.unit_price OR NEW.unit_cost IS DISTINCT FROM OLD.unit_cost
        OR NEW.chargeable <> OLD.chargeable OR NEW.price_overridden <> OLD.price_overridden THEN
        RAISE EXCEPTION 'Only the returned quantity of a repair part usage may grow'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

-- ---- public_portal_settings: the shareable home-service request page ----
-- One company-wide portal (single row): the customer chooses the nearest branch in the form
-- (BR-SRV-A1). Only the slug is stored; the full URL is built from the site's own origin.
CREATE TABLE public_portal_settings (
    id                      INTEGER      PRIMARY KEY,
    -- Off: the URL keeps working and shows a notice, but no new requests are accepted.
    enabled                 BOOLEAN      NOT NULL,
    slug                    VARCHAR(60)  NOT NULL,
    -- Request rules. A preferred date or window is a wish, never a confirmed visit (BR-SRV-001).
    allow_preferred_date    BOOLEAN      NOT NULL,
    allow_preferred_window  BOOLEAN      NOT NULL,
    min_notice_days         INTEGER      NOT NULL,
    max_days_ahead          INTEGER      NOT NULL,
    -- ISO weekdays (1 = Monday) a preferred date may fall on.
    service_days            JSONB        NOT NULL,
    -- Province codes (BR-SRV-A2) the business serves.
    served_provinces        JSONB        NOT NULL,
    -- Appliance types offered as options; empty = free text as before. "Other" is always allowed.
    service_types           JSONB        NOT NULL,
    -- Plain text only (rendered as text, never as HTML).
    welcome_message         VARCHAR(300),
    success_message         VARCHAR(500),
    updated_by              BIGINT,
    updated_at              TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_public_portal_settings_single CHECK (id = 1),
    CONSTRAINT uk_public_portal_settings_slug UNIQUE (slug),
    CONSTRAINT ck_public_portal_settings_slug
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND char_length(slug) BETWEEN 3 AND 60),
    CONSTRAINT ck_public_portal_settings_notice CHECK (min_notice_days BETWEEN 0 AND 30),
    CONSTRAINT ck_public_portal_settings_ahead
        CHECK (max_days_ahead BETWEEN 1 AND 180 AND max_days_ahead >= min_notice_days),
    CONSTRAINT ck_public_portal_settings_lists CHECK (jsonb_typeof(service_days) = 'array'
        AND jsonb_typeof(served_provinces) = 'array' AND jsonb_typeof(service_types) = 'array'),
    CONSTRAINT fk_public_portal_settings_updated_by FOREIGN KEY (updated_by) REFERENCES app_users (id)
);

-- Same behavior as before V12: open, any day up to 90 days ahead, every province, free text.
INSERT INTO public_portal_settings (id, enabled, slug, allow_preferred_date, allow_preferred_window,
                                    min_notice_days, max_days_ahead, service_days, served_provinces,
                                    service_types, welcome_message, success_message, updated_by, updated_at)
VALUES (1, TRUE, 'servicio-a-domicilio', TRUE, TRUE, 0, 90, '[1, 2, 3, 4, 5, 6, 7]',
        '["SAN_JOSE", "ALAJUELA", "CARTAGO", "HEREDIA", "GUANACASTE", "PUNTARENAS", "LIMON"]',
        '[]', NULL, NULL, NULL, now());

-- Previous slugs keep resolving to the current one, so printed QR codes and shared links survive a
-- change of address. Append-only.
CREATE TABLE public_portal_slug_history (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug         VARCHAR(60)  NOT NULL,
    replaced_by  VARCHAR(60)  NOT NULL,
    changed_by   BIGINT       NOT NULL,
    changed_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_public_portal_slug_history_changed_by FOREIGN KEY (changed_by) REFERENCES app_users (id),
    CONSTRAINT ck_public_portal_slug_history_distinct CHECK (slug <> replaced_by)
);

CREATE INDEX ix_public_portal_slug_history_slug ON public_portal_slug_history (slug);

CREATE TRIGGER trg_public_portal_slug_history_append_only
    BEFORE UPDATE OR DELETE ON public_portal_slug_history
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();
