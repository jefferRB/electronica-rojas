-- V9: Phase 4 - home-service requests, scheduled visits and technicians' working hours
-- (BR-SRV-001..008, BF-ARCH-001 ADR-013).
--
-- A request is what the customer asked for; a visit is what the business scheduled. The time of
-- a visit lives only in service_visits (one source of truth); rescheduling updates that row and
-- the change is kept in service_request_events.

-- Needed by the exclusion constraint below (equality on technician_id inside a GiST index).
-- Ships with PostgreSQL (contrib) and is a trusted extension.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE SEQUENCE service_request_number_seq;

CREATE TABLE service_requests (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_code            VARCHAR(20)   NOT NULL,
    -- Unguessable reference for the customer's status page; the readable code is not a lookup key.
    public_ref              UUID          NOT NULL,
    -- Client-generated id of the submission: a double submit creates one request.
    submission_id           UUID          NOT NULL,
    submission_fingerprint  VARCHAR(64)   NOT NULL,
    channel                 VARCHAR(20)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    branch_id               BIGINT        NOT NULL,
    -- Linked by staff after identity resolution; never automatically from the public form.
    customer_id             BIGINT,
    contact_name            VARCHAR(160)  NOT NULL,
    contact_phone           VARCHAR(16)   NOT NULL,
    contact_email           VARCHAR(254),
    province                VARCHAR(20)   NOT NULL,
    canton                  VARCHAR(80)   NOT NULL,
    district                VARCHAR(80),
    address_line            VARCHAR(300)  NOT NULL,
    device_type             VARCHAR(60)   NOT NULL,
    brand                   VARCHAR(60),
    model                   VARCHAR(80),
    problem_description     VARCHAR(1000) NOT NULL,
    preferred_date          DATE,
    preferred_window        VARCHAR(20)   NOT NULL,
    additional_notes        VARCHAR(500),
    -- Consent to be contacted about this request (required to submit) and, separately, to
    -- receive status notifications once a channel exists (BR-CUS-001, BR-NOT-001).
    contact_consent_at      TIMESTAMPTZ   NOT NULL,
    notifications_consent   BOOLEAN       NOT NULL,
    decision_reason         VARCHAR(500),
    created_by              BIGINT,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_service_requests_code UNIQUE (request_code),
    CONSTRAINT uk_service_requests_public_ref UNIQUE (public_ref),
    CONSTRAINT uk_service_requests_submission UNIQUE (submission_id),
    CONSTRAINT fk_service_requests_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_service_requests_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_service_requests_created_by FOREIGN KEY (created_by) REFERENCES app_users (id),
    CONSTRAINT ck_service_requests_code CHECK (request_code ~ '^SR-[0-9]{4}-[0-9]{6,}$'),
    CONSTRAINT ck_service_requests_channel CHECK (channel IN ('PUBLIC_FORM', 'STAFF')),
    CONSTRAINT ck_service_requests_status
        CHECK (status IN ('PENDING', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_service_requests_phone_e164 CHECK (contact_phone ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_service_requests_province CHECK (province IN
        ('SAN_JOSE', 'ALAJUELA', 'CARTAGO', 'HEREDIA', 'GUANACASTE', 'PUNTARENAS', 'LIMON')),
    CONSTRAINT ck_service_requests_window CHECK (preferred_window IN ('MORNING', 'AFTERNOON', 'ANY')),
    CONSTRAINT ck_service_requests_staff_author CHECK (channel = 'PUBLIC_FORM' OR created_by IS NOT NULL),
    -- Rejected and cancelled requests always say why.
    CONSTRAINT ck_service_requests_reason
        CHECK (status NOT IN ('REJECTED', 'CANCELLED') OR decision_reason IS NOT NULL),
    -- A request is accepted only once it belongs to a known customer.
    CONSTRAINT ck_service_requests_accepted_customer CHECK (status <> 'ACCEPTED' OR customer_id IS NOT NULL)
);

CREATE INDEX ix_service_requests_branch_status ON service_requests (branch_id, status, created_at DESC);
CREATE INDEX ix_service_requests_customer ON service_requests (customer_id) WHERE customer_id IS NOT NULL;

CREATE TABLE service_visits (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id        BIGINT        NOT NULL,
    -- Client operation id of "schedule visit": a double click creates one visit.
    operation_id      UUID          NOT NULL,
    technician_id     BIGINT        NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    scheduled_start   TIMESTAMPTZ   NOT NULL,
    scheduled_end     TIMESTAMPTZ   NOT NULL,
    -- scheduled_end + the margin before the technician's next visit (travel, set per branch).
    -- Stored because an index expression over timestamptz + interval would not be immutable.
    blocked_until     TIMESTAMPTZ   NOT NULL,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    outcome           VARCHAR(20),
    outcome_notes     VARCHAR(1000),
    cancel_reason     VARCHAR(500),
    repair_order_id   BIGINT,
    created_by        BIGINT        NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_service_visits_operation UNIQUE (operation_id),
    CONSTRAINT uk_service_visits_repair_order UNIQUE (repair_order_id),
    CONSTRAINT fk_service_visits_request FOREIGN KEY (request_id) REFERENCES service_requests (id),
    CONSTRAINT fk_service_visits_technician FOREIGN KEY (technician_id) REFERENCES app_users (id),
    CONSTRAINT fk_service_visits_repair_order FOREIGN KEY (repair_order_id) REFERENCES repair_orders (id),
    CONSTRAINT fk_service_visits_created_by FOREIGN KEY (created_by) REFERENCES app_users (id),
    CONSTRAINT ck_service_visits_status
        CHECK (status IN ('PROPOSED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_service_visits_times CHECK (scheduled_end > scheduled_start
        AND scheduled_end <= scheduled_start + INTERVAL '8 hours'
        AND blocked_until >= scheduled_end
        AND blocked_until <= scheduled_end + INTERVAL '4 hours'),
    CONSTRAINT ck_service_visits_outcome
        CHECK (outcome IS NULL OR outcome IN ('RESOLVED_ON_SITE', 'NEEDS_WORKSHOP', 'NOT_RESOLVED')),
    CONSTRAINT ck_service_visits_completed
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL AND outcome IS NOT NULL)),
    CONSTRAINT ck_service_visits_started CHECK (status NOT IN ('IN_PROGRESS', 'COMPLETED') OR started_at IS NOT NULL),
    CONSTRAINT ck_service_visits_cancel_reason CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL),
    CONSTRAINT ck_service_visits_repair_link CHECK (repair_order_id IS NULL OR outcome = 'NEEDS_WORKSHOP'),
    -- The database itself refuses two confirmed or running visits of one technician whose
    -- blocked ranges overlap, whatever the application does (BR-SRV-006).
    CONSTRAINT ex_service_visits_technician_overlap EXCLUDE USING gist (
        technician_id WITH =,
        tstzrange(scheduled_start, blocked_until, '[)') WITH &&
    ) WHERE (status IN ('CONFIRMED', 'IN_PROGRESS'))
);

-- At most one active visit per request: rescheduling moves it, it never duplicates it.
CREATE UNIQUE INDEX uk_service_visits_one_active ON service_visits (request_id)
    WHERE status IN ('PROPOSED', 'CONFIRMED', 'IN_PROGRESS');
CREATE INDEX ix_service_visits_technician_start ON service_visits (technician_id, scheduled_start);
CREATE INDEX ix_service_visits_start ON service_visits (scheduled_start);

-- Weekly working hours: one shift per technician and weekday, at one branch (BR-SRV-005).
CREATE TABLE technician_shifts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    technician_id   BIGINT       NOT NULL,
    branch_id       BIGINT       NOT NULL,
    day_of_week     SMALLINT     NOT NULL,
    start_time      TIME         NOT NULL,
    end_time        TIME         NOT NULL,
    break_start     TIME,
    break_end       TIME,
    updated_by      BIGINT       NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_technician_shifts_day UNIQUE (technician_id, day_of_week),
    CONSTRAINT fk_technician_shifts_technician FOREIGN KEY (technician_id) REFERENCES app_users (id),
    CONSTRAINT fk_technician_shifts_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_technician_shifts_updated_by FOREIGN KEY (updated_by) REFERENCES app_users (id),
    CONSTRAINT ck_technician_shifts_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_technician_shifts_hours CHECK (end_time > start_time),
    CONSTRAINT ck_technician_shifts_break CHECK (
        (break_start IS NULL AND break_end IS NULL)
        OR (break_start IS NOT NULL AND break_end IS NOT NULL
            AND break_start >= start_time AND break_end > break_start AND break_end <= end_time))
);

CREATE INDEX ix_technician_shifts_branch ON technician_shifts (branch_id);

-- Per-branch scheduling defaults the business can adjust (default values apply without a row).
CREATE TABLE branch_service_settings (
    branch_id               BIGINT       PRIMARY KEY,
    default_visit_minutes   INTEGER      NOT NULL,
    buffer_minutes          INTEGER      NOT NULL,
    updated_by              BIGINT       NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_branch_service_settings_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_branch_service_settings_updated_by FOREIGN KEY (updated_by) REFERENCES app_users (id),
    CONSTRAINT ck_branch_service_settings_visit CHECK (default_visit_minutes BETWEEN 15 AND 480),
    CONSTRAINT ck_branch_service_settings_buffer CHECK (buffer_minutes BETWEEN 0 AND 240)
);

-- Timeline of every decision on a request and its visits (actor NULL = the public form).
CREATE TABLE service_request_events (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id    BIGINT        NOT NULL,
    visit_id      BIGINT,
    event_type    VARCHAR(40)   NOT NULL,
    from_status   VARCHAR(20),
    to_status     VARCHAR(20),
    details       JSONB,
    reason        VARCHAR(500),
    actor_id      BIGINT,
    occurred_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_service_request_events_request FOREIGN KEY (request_id) REFERENCES service_requests (id),
    CONSTRAINT fk_service_request_events_visit FOREIGN KEY (visit_id) REFERENCES service_visits (id),
    CONSTRAINT fk_service_request_events_actor FOREIGN KEY (actor_id) REFERENCES app_users (id)
);

CREATE INDEX ix_service_request_events_request ON service_request_events (request_id, occurred_at, id);

CREATE TRIGGER trg_service_request_events_append_only
    BEFORE UPDATE OR DELETE ON service_request_events
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();

-- Requests and visits are business records: closed through statuses, never deleted.
CREATE TRIGGER trg_service_requests_no_delete
    BEFORE DELETE ON service_requests
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();

CREATE TRIGGER trg_service_visits_no_delete
    BEFORE DELETE ON service_visits
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();
