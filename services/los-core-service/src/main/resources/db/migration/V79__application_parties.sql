-- Multi-party (primary + co-applicant) support. Invoice-discounting flows ignore co-applicants at runtime.

CREATE TABLE IF NOT EXISTS application_parties (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id              UUID         NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
    role                        VARCHAR(30)  NOT NULL,
    sequence_no                 INTEGER      NOT NULL DEFAULT 0,
    user_id                     UUID,
    personal_info               JSONB,
    intake_status               VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    kyc_status                  VARCHAR(30)  NOT NULL DEFAULT 'NOT_STARTED',
    esign_status                VARCHAR(30)  NOT NULL DEFAULT 'NOT_STARTED',
    required_for_disbursement   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_application_parties_role CHECK (role IN ('PRIMARY', 'CO_APPLICANT'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_application_parties_one_primary
    ON application_parties (application_id)
    WHERE role = 'PRIMARY';

CREATE INDEX IF NOT EXISTS idx_application_parties_app
    ON application_parties (application_id);

CREATE INDEX IF NOT EXISTS idx_application_parties_user
    ON application_parties (user_id)
    WHERE user_id IS NOT NULL;

-- Backfill one PRIMARY party per existing application from personalInfo / businessInfo.
INSERT INTO application_parties (
    id, application_id, role, sequence_no, user_id, personal_info,
    intake_status, kyc_status, esign_status, required_for_disbursement, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    la.id,
    'PRIMARY',
    0,
    la.customer_id,
    CASE
        WHEN la.intake_segment = 'ANCHOR' THEN COALESCE(la.business_info, '{}'::jsonb)
        ELSE COALESCE(la.personal_info, '{}'::jsonb)
    END,
    CASE
        WHEN la.status IN ('DRAFT') THEN 'DRAFT'
        WHEN la.status IN ('CONSENT_PENDING') THEN 'INVITED'
        WHEN la.esign_transaction_id IS NOT NULL OR la.status IN ('ESIGN_COMPLETED', 'READY_FOR_DISBURSEMENT', 'DISBURSED') THEN 'ESIGN_COMPLETE'
        ELSE 'SUBMITTED'
    END,
    'NOT_STARTED',
    CASE
        WHEN la.esign_transaction_id IS NOT NULL OR la.status IN ('ESIGN_COMPLETED', 'READY_FOR_DISBURSEMENT', 'DISBURSED') THEN 'COMPLETE'
        ELSE 'NOT_STARTED'
    END,
    TRUE,
    COALESCE(la.created_at, NOW()),
    COALESCE(la.updated_at, NOW())
FROM loan_applications la
WHERE NOT EXISTS (
    SELECT 1 FROM application_parties ap WHERE ap.application_id = la.id AND ap.role = 'PRIMARY'
);

ALTER TABLE kyc_step_results ADD COLUMN IF NOT EXISTS party_id UUID;
CREATE INDEX IF NOT EXISTS idx_kyc_step_results_party ON kyc_step_results (application_id, party_id);

ALTER TABLE documents ADD COLUMN IF NOT EXISTS party_id UUID;
CREATE INDEX IF NOT EXISTS idx_documents_party ON documents (application_id, party_id);

ALTER TABLE esign_requests ADD COLUMN IF NOT EXISTS party_id UUID;
CREATE INDEX IF NOT EXISTS idx_esign_requests_party ON esign_requests (application_id, party_id);

ALTER TABLE manual_kyc_reviews ADD COLUMN IF NOT EXISTS party_id UUID;
ALTER TABLE manual_kyc_reviews DROP CONSTRAINT IF EXISTS uq_manual_kyc_reviews_app_step;
CREATE UNIQUE INDEX IF NOT EXISTS uq_manual_kyc_reviews_app_step_null_party
    ON manual_kyc_reviews (application_id, step_type)
    WHERE party_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_manual_kyc_reviews_app_party_step
    ON manual_kyc_reviews (application_id, party_id, step_type)
    WHERE party_id IS NOT NULL;
