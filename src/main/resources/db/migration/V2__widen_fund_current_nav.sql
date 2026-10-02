--liquibase formatted sql
-- Widens funds.current_nav. NUMERIC(10,4) caps the absolute value at 999,999.9999,
-- but the live AMFI feed publishes 12 schemes above 1,000,000 (max 2,540,201.9508,
-- e.g. scheme 130565 "IL&FS Infrastructure Debt Fund Series 1A"). Those rows made
-- the whole AMFI seed batch abort with SQLState 22003 "numeric field overflow",
-- which discarded all 9,251 rows because the sync writes in one transaction.
-- NUMERIC(18,4) leaves ~11 orders of magnitude of headroom.
--
-- return_rate_1_year / 3_year / 5_year stay NUMERIC(10,4): they are percentages
-- and are still null (AMFI does not publish them).

--changeset system:funds_widen_current_nav
--preconditions onFail:WARN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'funds' AND column_name = 'current_nav' AND numeric_precision = 10 AND numeric_scale = 4
ALTER TABLE funds ALTER COLUMN current_nav TYPE NUMERIC(18, 4);
