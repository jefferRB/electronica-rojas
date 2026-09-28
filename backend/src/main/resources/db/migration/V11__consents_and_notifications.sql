-- V11: Phase 5 - notification consent per channel and a transactional outbox of operational
-- notifications (BR-CUS-006, BR-NOT-001..006, BF-ARCH-001 ADR-016).
--
-- Nothing existing is reinterpreted as consent: customers start with no consent rows, and
-- service_requests.notifications_consent (a channel-less opt-in of the Phase 4 form) is kept as
-- it was and never converted.

-- ---- customer_consents: append-only log of what each customer said, per channel ----
-- The current consent of a (customer, channel) is its latest statement (stated_at, then id).
CREATE TABLE customer_consents (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id   BIGINT       NOT NULL,
    channel       VARCHAR(20)  NOT NULL,
    granted       BOOLEAN      NOT NULL,
    -- Where the customer said it: at the counter, by phone, in writing, or in the public form.
    source        VARCHAR(20)  NOT NULL,
    -- Version of the consent text the customer accepted (required to grant, optional to withdraw).
    text_version  VARCHAR(20),
    -- Business reference of the statement, e.g. the service request code of a public form.
    reference     VARCHAR(40),
    -- When the customer expressed it (the public form's submission time when applied later).
    stated_at     TIMESTAMPTZ  NOT NULL,
    recorded_by   BIGINT       NOT NULL,
    recorded_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_customer_consents_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_customer_consents_recorded_by FOREIGN KEY (recorded_by) REFERENCES app_users (id),
    CONSTRAINT ck_customer_consents_channel CHECK (channel IN ('EMAIL', 'WHATSAPP')),
    CONSTRAINT ck_customer_consents_source CHECK (source IN ('IN_PERSON', 'PHONE', 'WRITTEN', 'PUBLIC_FORM')),
    CONSTRAINT ck_customer_consents_text CHECK (NOT granted OR text_version IS NOT NULL)
);

CREATE INDEX ix_customer_consents_current ON customer_consents (customer_id, channel, stated_at DESC, id DESC);

CREATE TRIGGER trg_customer_consents_append_only
    BEFORE UPDATE OR DELETE ON customer_consents
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();

-- ---- service_requests: channel-specific opt-ins of the public form (from now on) ----
ALTER TABLE service_requests ADD COLUMN email_consent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE service_requests ADD COLUMN whatsapp_consent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE service_requests ADD COLUMN consent_text_version VARCHAR(20);
ALTER TABLE service_requests ADD CONSTRAINT ck_service_requests_consent_text
    CHECK (NOT (email_consent OR whatsapp_consent) OR consent_text_version IS NOT NULL);
ALTER TABLE service_requests ADD CONSTRAINT ck_service_requests_email_consent
    CHECK (NOT email_consent OR contact_email IS NOT NULL);

-- ---- notification_outbox: one message per business event, channel and customer ----
-- Written in the same transaction as the business change; a worker delivers it after commit.
CREATE TABLE notification_outbox (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- event type : source event id : channel : customer. A second enqueue of the same event is ignored.
    dedupe_key          VARCHAR(120) NOT NULL,
    event_type          VARCHAR(40)  NOT NULL,
    channel             VARCHAR(20)  NOT NULL,
    customer_id         BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    subject_type        VARCHAR(20)  NOT NULL,
    subject_id          BIGINT       NOT NULL,
    -- Template parameters only (codes, device, dates, branch): no internal notes, no diagnosis.
    payload             JSONB        NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    skip_reason         VARCHAR(30),
    attempts            INTEGER      NOT NULL DEFAULT 0,
    max_attempts        INTEGER      NOT NULL,
    next_attempt_at     TIMESTAMPTZ  NOT NULL,
    locked_by           VARCHAR(80),
    locked_until        TIMESTAMPTZ,
    last_error          VARCHAR(300),
    -- Masked address of the last attempt (l***@ejemplo.test), never the full address.
    recipient_hint      VARCHAR(120),
    provider_message_id VARCHAR(200),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    sent_at             TIMESTAMPTZ,
    CONSTRAINT uk_notification_outbox_dedupe UNIQUE (dedupe_key),
    CONSTRAINT fk_notification_outbox_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_notification_outbox_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT ck_notification_outbox_event CHECK (event_type IN
        ('REPAIR_READY_FOR_PICKUP', 'VISIT_CONFIRMED', 'VISIT_RESCHEDULED', 'VISIT_CANCELLED')),
    CONSTRAINT ck_notification_outbox_channel CHECK (channel IN ('EMAIL', 'WHATSAPP')),
    CONSTRAINT ck_notification_outbox_subject CHECK (subject_type IN ('REPAIR_ORDER', 'SERVICE_VISIT')),
    CONSTRAINT ck_notification_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_notification_outbox_skip CHECK ((status = 'SKIPPED') = (skip_reason IS NOT NULL)),
    CONSTRAINT ck_notification_outbox_sent CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    CONSTRAINT ck_notification_outbox_lease CHECK (status <> 'SENDING' OR (locked_by IS NOT NULL AND locked_until IS NOT NULL)),
    CONSTRAINT ck_notification_outbox_attempts CHECK (attempts >= 0 AND max_attempts > 0)
);

-- The worker's queue: due pending messages, oldest first.
CREATE INDEX ix_notification_outbox_due ON notification_outbox (next_attempt_at, id) WHERE status IN ('PENDING', 'SENDING');
CREATE INDEX ix_notification_outbox_subject ON notification_outbox (subject_type, subject_id);
CREATE INDEX ix_notification_outbox_branch_created ON notification_outbox (branch_id, created_at DESC, id DESC);
CREATE INDEX ix_notification_outbox_customer_pending ON notification_outbox (customer_id, channel) WHERE status = 'PENDING';

-- Outbox rows are operational records: they change status, but are never deleted.
CREATE TRIGGER trg_notification_outbox_no_delete
    BEFORE DELETE ON notification_outbox
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();

-- ---- notification_attempts: result of every delivery attempt, append-only ----
CREATE TABLE notification_attempts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    outbox_id       BIGINT       NOT NULL,
    attempt_number  INTEGER      NOT NULL,
    worker_id       VARCHAR(80)  NOT NULL,
    outcome         VARCHAR(20)  NOT NULL,
    -- Short technical code and message (no message content, no full address).
    error_code      VARCHAR(40),
    error_message   VARCHAR(300),
    started_at      TIMESTAMPTZ  NOT NULL,
    finished_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_notification_attempts_outbox FOREIGN KEY (outbox_id) REFERENCES notification_outbox (id),
    CONSTRAINT ck_notification_attempts_outcome CHECK (outcome IN ('SENT', 'RETRY', 'FAILED', 'SKIPPED'))
);

CREATE INDEX ix_notification_attempts_outbox ON notification_attempts (outbox_id, id);

CREATE TRIGGER trg_notification_attempts_append_only
    BEFORE UPDATE OR DELETE ON notification_attempts
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();
