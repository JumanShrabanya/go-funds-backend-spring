--liquibase formatted sql
-- This changelog is idempotent: each object is only created when it does
-- not already exist. Pre-existing tables (with data) are marked as applied.

--changeset system:users
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users'
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    role            VARCHAR(50)  NOT NULL DEFAULT 'USER',
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--changeset system:refresh_tokens
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'refresh_tokens'
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refresh_tokens_user_token UNIQUE (user_id, token_hash)
);

--changeset system:idx_refresh_tokens_user_id
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_refresh_tokens_user_id'
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

--changeset system:email_otps
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'email_otps'
CREATE TABLE email_otps (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    otp_hash    VARCHAR(255) NOT NULL,
    otp_type    VARCHAR(50)  NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--changeset system:idx_email_otps_user_id
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_email_otps_user_id'
CREATE INDEX idx_email_otps_user_id ON email_otps (user_id);

--changeset system:funds
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'funds'
CREATE TABLE funds (
    id                  UUID PRIMARY KEY,
    scheme_code         VARCHAR(100)  NOT NULL UNIQUE,
    scheme_name         VARCHAR(255)  NOT NULL,
    fund_house          VARCHAR(150),
    main_category       VARCHAR(100)  NOT NULL,
    sub_category        VARCHAR(100)  NOT NULL,
    risk_level          VARCHAR(50)   NOT NULL,
    current_nav         NUMERIC(10, 4) NOT NULL,
    return_rate_1_year  NUMERIC(10, 4),
    return_rate_3_year  NUMERIC(10, 4),
    return_rate_5_year  NUMERIC(10, 4),
    supports_sip        BOOLEAN       NOT NULL DEFAULT TRUE,
    supports_lump_sum   BOOLEAN       NOT NULL DEFAULT TRUE,
    fact_sheet          TEXT,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_synced_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--changeset system:idx_funds_main_category
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_funds_main_category'
CREATE INDEX idx_funds_main_category ON funds (main_category);

--changeset system:idx_funds_risk_level
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_funds_risk_level'
CREATE INDEX idx_funds_risk_level ON funds (risk_level);

--changeset system:idx_funds_scheme_code
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_funds_scheme_code'
CREATE INDEX idx_funds_scheme_code ON funds (scheme_code);

--changeset system:investment_plans
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'investment_plans'
CREATE TABLE investment_plans (
    id                    UUID PRIMARY KEY,
    user_id               UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    goal                  VARCHAR(50) NOT NULL,
    investment_horizon    VARCHAR(50) NOT NULL,
    monthly_amount        NUMERIC(15, 2) NOT NULL,
    risk_profile          VARCHAR(50) NOT NULL,
    allocation_breakdown  JSONB,
    recommended_funds     JSONB,
    projected_returns     NUMERIC(15, 2),
    explanation           TEXT,
    status                VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP
);

--changeset system:idx_investment_plans_user_id
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_investment_plans_user_id'
CREATE INDEX idx_investment_plans_user_id ON investment_plans (user_id);

--changeset system:idx_investment_plans_status
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_investment_plans_status'
CREATE INDEX idx_investment_plans_status ON investment_plans (status);