-- V2: Phase 1 - branches, collaborator accounts and branch assignments.
-- IDs follow ADR-007 (BIGINT identity). Nothing is ever deleted physically (DATA-005):
-- branches and users are deactivated, so foreign keys keep the default NO ACTION.

CREATE TABLE branches (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    address     VARCHAR(300),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_branches_code UNIQUE (code),
    -- Codes are stored normalized (upper case) so uniqueness is case-insensitive (BR-BRH-001).
    CONSTRAINT ck_branches_code_format CHECK (code ~ '^[A-Z0-9][A-Z0-9-]{1,19}$'),
    CONSTRAINT ck_branches_name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE app_users (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email            VARCHAR(254) NOT NULL,
    full_name        VARCHAR(120) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    role             VARCHAR(30)  NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Incremented when role, status or password change; open sessions with an older value
    -- are revoked on their next request (BR-BRH-004).
    security_version BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_email_normalized CHECK (email = lower(btrim(email)) AND email <> ''),
    CONSTRAINT ck_app_users_full_name_not_blank CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST', 'TECHNICIAN'))
);

-- UserBranch: explicit authorization of a collaborator to a branch (BR-BRH-002, ARCH-SEC-005).
CREATE TABLE user_branches (
    user_id   BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    CONSTRAINT pk_user_branches PRIMARY KEY (user_id, branch_id),
    CONSTRAINT fk_user_branches_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_user_branches_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
);

-- The primary key already serves lookups by user_id; this one serves "who works at branch X".
CREATE INDEX ix_user_branches_branch_id ON user_branches (branch_id);
