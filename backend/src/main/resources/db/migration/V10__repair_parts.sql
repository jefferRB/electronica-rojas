-- V10: Phase 5 - spare parts used in repair orders (BR-REP-007, BR-REP-011..013).
--
-- A part is company stock until it is consumed: the consumption is an OUT_FOR_REPAIR stock
-- movement linked to the order, written by the inventory ledger in the same transaction as the
-- part line. A correction never deletes or rewrites anything: it is a RETURN_FROM_REPAIR movement
-- plus a return row, and the line keeps a running total of what went back to the shelf.

-- ---- stock_movements: two new movement types linked to a repair order ----
-- The table is append-only (trigger from V3). Adding a nullable column and replacing CHECK
-- constraints does not update any row, so the history stays untouched.
ALTER TABLE stock_movements ADD COLUMN repair_order_id BIGINT;
ALTER TABLE stock_movements ADD CONSTRAINT fk_stock_movements_repair_order
    FOREIGN KEY (repair_order_id) REFERENCES repair_orders (id);

ALTER TABLE stock_movements DROP CONSTRAINT ck_stock_movements_type;
ALTER TABLE stock_movements ADD CONSTRAINT ck_stock_movements_type CHECK (type IN
    ('RECEIPT', 'ISSUE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER_OUT', 'TRANSFER_IN',
     'OUT_FOR_REPAIR', 'RETURN_FROM_REPAIR'));

ALTER TABLE stock_movements DROP CONSTRAINT ck_stock_movements_delta_sign;
ALTER TABLE stock_movements ADD CONSTRAINT ck_stock_movements_delta_sign CHECK (
    (type IN ('RECEIPT', 'ADJUSTMENT_IN', 'TRANSFER_IN', 'RETURN_FROM_REPAIR') AND quantity_delta > 0)
    OR (type IN ('ISSUE', 'ADJUSTMENT_OUT', 'TRANSFER_OUT', 'OUT_FOR_REPAIR') AND quantity_delta < 0));

-- Repair movements always point to their order; no other movement does.
ALTER TABLE stock_movements ADD CONSTRAINT ck_stock_movements_repair_link CHECK (
    (type IN ('OUT_FOR_REPAIR', 'RETURN_FROM_REPAIR')) = (repair_order_id IS NOT NULL));

-- A consumption is explained by its order (the note is optional); a return always says why.
ALTER TABLE stock_movements DROP CONSTRAINT ck_stock_movements_reason;
ALTER TABLE stock_movements ADD CONSTRAINT ck_stock_movements_reason CHECK (
    type IN ('TRANSFER_OUT', 'TRANSFER_IN', 'OUT_FOR_REPAIR') OR (reason IS NOT NULL AND btrim(reason) <> ''));

CREATE INDEX ix_stock_movements_repair_order ON stock_movements (repair_order_id) WHERE repair_order_id IS NOT NULL;

-- ---- repair_part_usages: one line per part consumed in an order ----
CREATE TABLE repair_part_usages (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id            BIGINT       NOT NULL,
    product_id          BIGINT       NOT NULL,
    -- Branch whose stock was used (the order's branch); kept explicitly for the history.
    branch_id           BIGINT       NOT NULL,
    quantity            INTEGER      NOT NULL,
    -- Units already given back to the shelf through repair_part_returns.
    returned_quantity   INTEGER      NOT NULL DEFAULT 0,
    -- Client operation id: a double click or a retry records the line once.
    operation_id        UUID         NOT NULL,
    request_fingerprint VARCHAR(64)  NOT NULL,
    movement_id         BIGINT       NOT NULL,
    note                VARCHAR(300),
    recorded_by         BIGINT       NOT NULL,
    recorded_at         TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_repair_part_usages_operation UNIQUE (operation_id),
    CONSTRAINT uk_repair_part_usages_movement UNIQUE (movement_id),
    CONSTRAINT fk_repair_part_usages_order FOREIGN KEY (order_id) REFERENCES repair_orders (id),
    CONSTRAINT fk_repair_part_usages_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_repair_part_usages_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_repair_part_usages_movement FOREIGN KEY (movement_id) REFERENCES stock_movements (id),
    CONSTRAINT fk_repair_part_usages_recorded_by FOREIGN KEY (recorded_by) REFERENCES app_users (id),
    CONSTRAINT ck_repair_part_usages_quantity CHECK (quantity > 0 AND quantity <= 1000),
    -- Never more returned than consumed: the database refuses a double return (BR-REP-012).
    CONSTRAINT ck_repair_part_usages_returned CHECK (returned_quantity >= 0 AND returned_quantity <= quantity)
);

CREATE INDEX ix_repair_part_usages_order ON repair_part_usages (order_id, recorded_at, id);

-- ---- repair_part_returns: corrections, append-only ----
CREATE TABLE repair_part_returns (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usage_id            BIGINT       NOT NULL,
    quantity            INTEGER      NOT NULL,
    reason              VARCHAR(300) NOT NULL,
    operation_id        UUID         NOT NULL,
    request_fingerprint VARCHAR(64)  NOT NULL,
    movement_id         BIGINT       NOT NULL,
    -- Order status when the correction was made: corrections of closed orders stand out.
    order_status        VARCHAR(30)  NOT NULL,
    recorded_by         BIGINT       NOT NULL,
    recorded_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_repair_part_returns_operation UNIQUE (operation_id),
    CONSTRAINT uk_repair_part_returns_movement UNIQUE (movement_id),
    CONSTRAINT fk_repair_part_returns_usage FOREIGN KEY (usage_id) REFERENCES repair_part_usages (id),
    CONSTRAINT fk_repair_part_returns_movement FOREIGN KEY (movement_id) REFERENCES stock_movements (id),
    CONSTRAINT fk_repair_part_returns_recorded_by FOREIGN KEY (recorded_by) REFERENCES app_users (id),
    CONSTRAINT ck_repair_part_returns_quantity CHECK (quantity > 0),
    CONSTRAINT ck_repair_part_returns_reason CHECK (btrim(reason) <> '')
);

CREATE INDEX ix_repair_part_returns_usage ON repair_part_returns (usage_id, recorded_at, id);

CREATE TRIGGER trg_repair_part_returns_append_only
    BEFORE UPDATE OR DELETE ON repair_part_returns
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();

-- A usage line is never deleted, and only its running return total (plus version) may change,
-- and only upwards: the consumed quantity, product, branch and author are history.
CREATE FUNCTION guard_repair_part_usage() RETURNS trigger
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
        OR NEW.recorded_at <> OLD.recorded_at OR NEW.returned_quantity < OLD.returned_quantity THEN
        RAISE EXCEPTION 'Only the returned quantity of a repair part usage may grow'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_repair_part_usages_guard
    BEFORE UPDATE OR DELETE ON repair_part_usages
    FOR EACH ROW EXECUTE FUNCTION guard_repair_part_usage();
