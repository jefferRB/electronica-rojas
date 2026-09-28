-- V7: repair orders are custody records: they are closed through statuses, never deleted
-- (BR-REP-003, DATA-005). Reuses reject_history_change() from V3.
-- Kept apart from V6 because V6 was already applied when this rule was added (see BF-ARCH-001
-- ADR-012): an applied migration is never edited.
CREATE TRIGGER trg_repair_orders_no_delete
    BEFORE DELETE ON repair_orders
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();
