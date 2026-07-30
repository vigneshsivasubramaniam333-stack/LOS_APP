-- Additional commercial / eligibility fields for invoice-discounting programs (LOS → PLP sync).

ALTER TABLE program_masters
    ADD COLUMN IF NOT EXISTS interest_payment VARCHAR(20),
    ADD COLUMN IF NOT EXISTS max_invoice_vintage_days INTEGER,
    ADD COLUMN IF NOT EXISTS max_cmr INTEGER,
    ADD COLUMN IF NOT EXISTS min_cibil INTEGER;

COMMENT ON COLUMN program_masters.tenure_days IS 'Max tenor (days)';
COMMENT ON COLUMN program_masters.interest_payment IS 'UPFRONT | MONTHLY | REAR_ENDED';
COMMENT ON COLUMN program_masters.max_invoice_vintage_days IS 'Max invoice vintage / age (days); synced to PLP config.maxInvoiceAgeDays';
COMMENT ON COLUMN program_masters.anchor_relationship_vintage_months IS 'Min director / direct relationship (months)';
COMMENT ON COLUMN program_masters.max_cmr IS 'Maximum allowed CMR';
COMMENT ON COLUMN program_masters.min_cibil IS 'Minimum required CIBIL score';
