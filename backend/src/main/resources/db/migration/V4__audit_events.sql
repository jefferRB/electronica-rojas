-- V4: functional audit trail (OBS-002, BR-SEC-002, FR-AUD-001), separate from technical logs.
-- Reusable by later modules (repairs, service requests). Stores who did what and when, with a
-- short non-sensitive summary: never passwords, hashes, cookies, tokens or request bodies.

CREATE TABLE audit_events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    -- NULL actor = system action (e.g. the configured admin bootstrap).
    actor_id       BIGINT,
    -- Name snapshot: the trail keeps reading correctly if the account is renamed later.
    actor_name     VARCHAR(120),
    action         VARCHAR(60)  NOT NULL,
    entity_type    VARCHAR(40)  NOT NULL,
    entity_id      VARCHAR(40)  NOT NULL,
    branch_id      BIGINT,
    operation_id   UUID,
    correlation_id VARCHAR(64),
    summary        VARCHAR(300) NOT NULL,
    CONSTRAINT fk_audit_events_actor FOREIGN KEY (actor_id) REFERENCES app_users (id),
    CONSTRAINT fk_audit_events_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT ck_audit_events_actor_name CHECK ((actor_id IS NULL) = (actor_name IS NULL))
);

CREATE INDEX ix_audit_events_occurred ON audit_events (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_events_branch_occurred ON audit_events (branch_id, occurred_at DESC) WHERE branch_id IS NOT NULL;

CREATE TRIGGER trg_audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();
