-- V5: structured audit details (Phase 3, A.2).
-- New events store machine-readable data (codes, quantities, branch names at the time of the
-- action) so the UI can render them in Spanish without parsing English text. Existing rows are
-- NOT rewritten (the table is append-only): their details stay NULL and the API interprets their
-- legacy summary instead.

ALTER TABLE audit_events ADD COLUMN details JSONB;

-- Audit filters by action and date range (FR-AUD-001).
CREATE INDEX ix_audit_events_action_occurred ON audit_events (action, occurred_at DESC);
