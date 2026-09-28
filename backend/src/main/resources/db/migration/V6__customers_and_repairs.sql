-- V6: Phase 3 - customers and in-shop repair orders (BR-CUS-001/002, BR-REP-001..007).
-- A customer's appliance is property in custody: it never touches products or branch_stock.

CREATE TABLE customers (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name            VARCHAR(160)  NOT NULL,
    -- E.164, e.g. +50688887777. Not unique: relatives may share a number (BR-CUS-002).
    phone                VARCHAR(16)   NOT NULL,
    email                VARCHAR(254),
    address              VARCHAR(300),
    internal_notes       VARCHAR(1000),
    -- Branch where the customer was first registered; with the branches of their repair orders it
    -- decides which collaborators may see them.
    registered_branch_id BIGINT        NOT NULL,
    created_by           BIGINT        NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_customers_registered_branch FOREIGN KEY (registered_branch_id) REFERENCES branches (id),
    CONSTRAINT fk_customers_created_by FOREIGN KEY (created_by) REFERENCES app_users (id),
    CONSTRAINT ck_customers_name_not_blank CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_customers_phone_e164 CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_customers_email_normalized CHECK (email IS NULL OR (email = lower(btrim(email)) AND email <> ''))
);

CREATE INDEX ix_customers_phone ON customers (phone);
CREATE INDEX ix_customers_registered_branch ON customers (registered_branch_id);

-- Human-readable order numbers OR-2026-000042 (BR-REP-002); gaps are harmless.
CREATE SEQUENCE repair_order_number_seq;

CREATE TABLE repair_orders (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_code             VARCHAR(20)   NOT NULL,
    -- Client operation id of the reception form: a double submit creates one order.
    intake_operation_id    UUID          NOT NULL,
    intake_fingerprint     VARCHAR(64)   NOT NULL,
    customer_id            BIGINT        NOT NULL,
    branch_id              BIGINT        NOT NULL,
    -- Snapshot of the appliance at each intake: the same device returning later is a new order
    -- (compare serial numbers), without a separate asset registry.
    device_type            VARCHAR(60)   NOT NULL,
    brand                  VARCHAR(60)   NOT NULL,
    model                  VARCHAR(80),
    serial_number          VARCHAR(80),
    reported_fault         VARCHAR(1000) NOT NULL,
    physical_condition     VARCHAR(1000) NOT NULL,
    accessories            VARCHAR(500),
    status                 VARCHAR(30)   NOT NULL,
    -- How the work ended, set with READY_FOR_PICKUP / CANCELLED / UNREPAIRABLE.
    resolution             VARCHAR(20),
    assigned_technician_id BIGINT,
    diagnosis              VARCHAR(2000),
    diagnosis_updated_at   TIMESTAMPTZ,
    diagnosis_updated_by   BIGINT,
    received_at            TIMESTAMPTZ   NOT NULL,
    received_by            BIGINT        NOT NULL,
    -- Physical hand-over to the customer (repaired or not). NULL = still in custody.
    delivered_at           TIMESTAMPTZ,
    delivered_by           BIGINT,
    updated_at             TIMESTAMPTZ   NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_repair_orders_code UNIQUE (order_code),
    CONSTRAINT uk_repair_orders_intake_operation UNIQUE (intake_operation_id),
    CONSTRAINT fk_repair_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_repair_orders_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_repair_orders_technician FOREIGN KEY (assigned_technician_id) REFERENCES app_users (id),
    CONSTRAINT fk_repair_orders_diagnosis_by FOREIGN KEY (diagnosis_updated_by) REFERENCES app_users (id),
    CONSTRAINT fk_repair_orders_received_by FOREIGN KEY (received_by) REFERENCES app_users (id),
    CONSTRAINT fk_repair_orders_delivered_by FOREIGN KEY (delivered_by) REFERENCES app_users (id),
    CONSTRAINT ck_repair_orders_code CHECK (order_code ~ '^OR-[0-9]{4}-[0-9]{6,}$'),
    CONSTRAINT ck_repair_orders_status CHECK (status IN ('RECEIVED', 'DIAGNOSING', 'AWAITING_APPROVAL', 'APPROVED',
        'IN_REPAIR', 'READY_FOR_PICKUP', 'DELIVERED', 'CANCELLED', 'UNREPAIRABLE')),
    CONSTRAINT ck_repair_orders_resolution CHECK (resolution IS NULL OR resolution IN ('REPAIRED', 'CANCELLED', 'UNREPAIRABLE')),
    CONSTRAINT ck_repair_orders_text_not_blank CHECK (btrim(device_type) <> '' AND btrim(brand) <> ''
        AND btrim(reported_fault) <> '' AND btrim(physical_condition) <> ''),
    CONSTRAINT ck_repair_orders_delivery_pair CHECK ((delivered_at IS NULL) = (delivered_by IS NULL)),
    -- Delivered exactly when handed over; never delivered twice (DELIVERED is terminal).
    CONSTRAINT ck_repair_orders_delivered CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL)),
    CONSTRAINT ck_repair_orders_delivered_resolution CHECK (status <> 'DELIVERED' OR resolution IS NOT NULL)
);

CREATE INDEX ix_repair_orders_branch_received ON repair_orders (branch_id, received_at DESC, id DESC);
CREATE INDEX ix_repair_orders_customer ON repair_orders (customer_id, received_at DESC);
CREATE INDEX ix_repair_orders_technician_status ON repair_orders (assigned_technician_id, status)
    WHERE assigned_technician_id IS NOT NULL;
CREATE INDEX ix_repair_orders_serial ON repair_orders (serial_number) WHERE serial_number IS NOT NULL;

-- Immutable timeline of every status change (BR-REP-003).
CREATE TABLE repair_status_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT       NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30)  NOT NULL,
    reason      VARCHAR(500),
    actor_id    BIGINT       NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_repair_history_order FOREIGN KEY (order_id) REFERENCES repair_orders (id),
    CONSTRAINT fk_repair_history_actor FOREIGN KEY (actor_id) REFERENCES app_users (id),
    CONSTRAINT ck_repair_history_changed CHECK (from_status IS DISTINCT FROM to_status)
);

CREATE INDEX ix_repair_history_order ON repair_status_history (order_id, changed_at, id);

CREATE TRIGGER trg_repair_status_history_append_only
    BEFORE UPDATE OR DELETE ON repair_status_history
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();

-- Quotes in Costa Rican colones (NUMERIC, never floating point: DATA-006).
CREATE TABLE repair_quotes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        BIGINT         NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    currency        CHAR(3)        NOT NULL DEFAULT 'CRC',
    description     VARCHAR(1000)  NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    created_by      BIGINT         NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    decided_by      BIGINT,
    decided_at      TIMESTAMPTZ,
    decision_method VARCHAR(20),
    decision_note   VARCHAR(500),
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_repair_quotes_order FOREIGN KEY (order_id) REFERENCES repair_orders (id),
    CONSTRAINT fk_repair_quotes_created_by FOREIGN KEY (created_by) REFERENCES app_users (id),
    CONSTRAINT fk_repair_quotes_decided_by FOREIGN KEY (decided_by) REFERENCES app_users (id),
    CONSTRAINT ck_repair_quotes_amount CHECK (amount > 0),
    CONSTRAINT ck_repair_quotes_currency CHECK (currency = 'CRC'),
    CONSTRAINT ck_repair_quotes_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_repair_quotes_method CHECK (decision_method IS NULL
        OR decision_method IN ('IN_PERSON', 'PHONE', 'EMAIL', 'MESSAGE')),
    -- A decision needs who, when and how; a pending quote has none of them.
    CONSTRAINT ck_repair_quotes_decision CHECK (
        (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL AND decision_method IS NULL)
        OR (status <> 'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND decision_method IS NOT NULL))
);

CREATE INDEX ix_repair_quotes_order ON repair_quotes (order_id, created_at);
-- At most one quote awaiting the customer per order.
CREATE UNIQUE INDEX uk_repair_quotes_one_pending ON repair_quotes (order_id) WHERE status = 'PENDING';

-- A decided quote is final: a rejected quote can never become approved, and quotes are never deleted.
CREATE FUNCTION reject_decided_quote_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE' OR OLD.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Decided or deleted repair quotes are immutable' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_repair_quotes_decided_immutable
    BEFORE UPDATE OR DELETE ON repair_quotes
    FOR EACH ROW EXECUTE FUNCTION reject_decided_quote_change();
