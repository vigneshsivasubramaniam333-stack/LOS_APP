-- Program custom field definitions catalog + values jsonb on program_masters

CREATE TABLE IF NOT EXISTS program_field_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_key       VARCHAR(100) NOT NULL,
    label           VARCHAR(200) NOT NULL,
    input_type      VARCHAR(20)  NOT NULL,
    options_json    JSONB,
    required        BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    product_types   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    system_managed  BOOLEAN      NOT NULL DEFAULT FALSE,
    help_text       VARCHAR(500),
    storage_target  VARCHAR(50)  NOT NULL DEFAULT 'CUSTOM',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_program_field_definitions_key UNIQUE (field_key),
    CONSTRAINT chk_program_field_input_type CHECK (input_type IN ('TEXT', 'NUMBER', 'DROPDOWN'))
);

CREATE INDEX IF NOT EXISTS idx_program_field_definitions_active
    ON program_field_definitions (active, sort_order);

ALTER TABLE program_masters
    ADD COLUMN IF NOT EXISTS custom_fields JSONB DEFAULT '{}'::jsonb;

COMMENT ON COLUMN program_masters.custom_fields IS
    'Flexible program-level custom field values keyed by program_field_definitions.field_key';

-- System-managed eligibility / commercial knobs (dual-written to typed columns)
INSERT INTO program_field_definitions (
    field_key, label, input_type, options_json, required, active, sort_order,
    product_types, system_managed, help_text, storage_target
) VALUES
(
    'anchorRelationshipVintageMonths',
    'Min Dir Relationship (months)',
    'NUMBER',
    NULL,
    FALSE,
    TRUE,
    10,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'Minimum director / direct relationship vintage in months',
    'anchorRelationshipVintageMonths'
),
(
    'interestPayment',
    'Interest payment',
    'DROPDOWN',
    '[{"value":"UPFRONT","label":"Upfront"},{"value":"MONTHLY","label":"Monthly"},{"value":"REAR_ENDED","label":"Rear ended"}]'::jsonb,
    FALSE,
    TRUE,
    20,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'When interest is collected',
    'interestPayment'
),
(
    'maxInvoiceVintageDays',
    'Max invoice vintage (days)',
    'NUMBER',
    NULL,
    FALSE,
    TRUE,
    30,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'Maximum allowed invoice age in days',
    'maxInvoiceVintageDays'
),
(
    'maxCmr',
    'Max CMR',
    'NUMBER',
    NULL,
    FALSE,
    TRUE,
    40,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'Maximum commercial credit rating (CMR)',
    'maxCmr'
),
(
    'minCibil',
    'Min CIBIL',
    'NUMBER',
    NULL,
    FALSE,
    TRUE,
    50,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'Minimum CIBIL score required',
    'minCibil'
),
(
    'tenureDays',
    'Max tenor (days)',
    'NUMBER',
    NULL,
    FALSE,
    TRUE,
    60,
    '["INVOICE_DISCOUNTING"]'::jsonb,
    TRUE,
    'Maximum program tenure / tenor in days',
    'tenureDays'
)
ON CONFLICT (field_key) DO NOTHING;
