-- V3: Phase 2 - global catalog, per-branch stock, stock movements and transfers.
-- Invariants live in the database too (DATA-001): no negative stock, one row per
-- (branch, product), movement signs consistent with their type, immutable history.

CREATE TABLE products (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku         VARCHAR(40)   NOT NULL,
    name        VARCHAR(160)  NOT NULL,
    category    VARCHAR(80)   NOT NULL,
    description VARCHAR(1000),
    -- MERCHANDISE: sold to customers; SPARE_PART: used in repairs (consumption arrives later).
    kind        VARCHAR(20)   NOT NULL,
    -- MVP counts whole units only (BR-INV-001/002).
    unit        VARCHAR(10)   NOT NULL DEFAULT 'UNIT',
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_sku_format CHECK (sku ~ '^[A-Z0-9][A-Z0-9._-]{1,39}$'),
    CONSTRAINT ck_products_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_products_category_not_blank CHECK (btrim(category) <> ''),
    CONSTRAINT ck_products_kind CHECK (kind IN ('MERCHANDISE', 'SPARE_PART')),
    CONSTRAINT ck_products_unit CHECK (unit = 'UNIT')
);

CREATE INDEX ix_products_category ON products (category);

-- BranchStock: physical units of a product at a branch (BR-INV-002). The row is created on the
-- first movement with INSERT ... ON CONFLICT DO NOTHING, which is race-safe thanks to the
-- unique constraint (ARCH-DB-004).
CREATE TABLE branch_stock (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    branch_id        BIGINT      NOT NULL,
    product_id       BIGINT      NOT NULL,
    quantity         INTEGER     NOT NULL DEFAULT 0,
    minimum_quantity INTEGER     NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_branch_stock_branch_product UNIQUE (branch_id, product_id),
    CONSTRAINT fk_branch_stock_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_branch_stock_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_branch_stock_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT ck_branch_stock_minimum_non_negative CHECK (minimum_quantity >= 0)
);

CREATE INDEX ix_branch_stock_product ON branch_stock (product_id);

-- StockTransfer header (ARCH-DB-003): one idempotent operation, two movements.
CREATE TABLE stock_transfers (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operation_id          UUID         NOT NULL,
    source_branch_id      BIGINT       NOT NULL,
    destination_branch_id BIGINT       NOT NULL,
    product_id            BIGINT       NOT NULL,
    quantity              INTEGER      NOT NULL,
    reason                VARCHAR(300),
    request_fingerprint   VARCHAR(64)  NOT NULL,
    actor_id              BIGINT       NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_stock_transfers_operation UNIQUE (operation_id),
    CONSTRAINT fk_stock_transfers_source FOREIGN KEY (source_branch_id) REFERENCES branches (id),
    CONSTRAINT fk_stock_transfers_destination FOREIGN KEY (destination_branch_id) REFERENCES branches (id),
    CONSTRAINT fk_stock_transfers_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_transfers_actor FOREIGN KEY (actor_id) REFERENCES app_users (id),
    CONSTRAINT ck_stock_transfers_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_stock_transfers_distinct_branches CHECK (source_branch_id <> destination_branch_id)
);

-- StockMovement: immutable evidence of every stock change (BR-INV-003).
-- operation_id groups the movements of one operation: one for receipts/issues/adjustments,
-- two (one per branch) for a transfer. UNIQUE (operation_id, branch_id) makes a replayed
-- operation impossible to apply twice even if the service check were bypassed.
CREATE TABLE stock_movements (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operation_id        UUID         NOT NULL,
    type                VARCHAR(20)  NOT NULL,
    branch_id           BIGINT       NOT NULL,
    product_id          BIGINT       NOT NULL,
    quantity_delta      INTEGER      NOT NULL,
    balance_after       INTEGER      NOT NULL,
    reason              VARCHAR(300),
    request_fingerprint VARCHAR(64)  NOT NULL,
    transfer_id         BIGINT,
    actor_id            BIGINT       NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_stock_movements_operation_branch UNIQUE (operation_id, branch_id),
    CONSTRAINT fk_stock_movements_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_movements_transfer FOREIGN KEY (transfer_id) REFERENCES stock_transfers (id),
    CONSTRAINT fk_stock_movements_actor FOREIGN KEY (actor_id) REFERENCES app_users (id),
    CONSTRAINT ck_stock_movements_type CHECK (type IN
        ('RECEIPT', 'ISSUE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER_OUT', 'TRANSFER_IN')),
    CONSTRAINT ck_stock_movements_delta_sign CHECK (
        (type IN ('RECEIPT', 'ADJUSTMENT_IN', 'TRANSFER_IN') AND quantity_delta > 0)
        OR (type IN ('ISSUE', 'ADJUSTMENT_OUT', 'TRANSFER_OUT') AND quantity_delta < 0)),
    CONSTRAINT ck_stock_movements_balance_non_negative CHECK (balance_after >= 0),
    -- Transfer legs point to their transfer; standalone movements must explain why (BR-INV-003).
    CONSTRAINT ck_stock_movements_transfer_link CHECK (
        (type IN ('TRANSFER_OUT', 'TRANSFER_IN')) = (transfer_id IS NOT NULL)),
    CONSTRAINT ck_stock_movements_reason CHECK (
        type IN ('TRANSFER_OUT', 'TRANSFER_IN') OR (reason IS NOT NULL AND btrim(reason) <> ''))
);

-- History screens: newest movements of a branch, optionally for one product.
CREATE INDEX ix_stock_movements_branch_created ON stock_movements (branch_id, created_at DESC, id DESC);
CREATE INDEX ix_stock_movements_product_created ON stock_movements (product_id, created_at DESC);
CREATE INDEX ix_stock_movements_transfer ON stock_movements (transfer_id) WHERE transfer_id IS NOT NULL;

-- History is never rewritten (BR-TRF-005, DATA-005): corrections are new compensating
-- operations. The database rejects UPDATE and DELETE on these tables.
CREATE FUNCTION reject_history_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Table % is append-only', TG_TABLE_NAME USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER trg_stock_movements_append_only
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();

CREATE TRIGGER trg_stock_transfers_append_only
    BEFORE UPDATE OR DELETE ON stock_transfers
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();
