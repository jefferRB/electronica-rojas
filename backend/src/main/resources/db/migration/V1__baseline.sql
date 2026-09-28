-- V1: Phase 0 baseline (ARCH-DB-006).
-- Intentionally creates no business tables. Its purpose is to prove that Flyway runs
-- against PostgreSQL and records flyway_schema_history. Branches, users and inventory
-- arrive in their own versioned migrations (V2+). Never edit this file once applied.

COMMENT ON SCHEMA public IS 'BranchFix application schema, managed by Flyway';
