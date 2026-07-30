-- Anchor delegated intake + post-eSign document verification notes

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS anchor_sent_back_notes TEXT,
    ADD COLUMN IF NOT EXISTS doc_verification_notes TEXT;

COMMENT ON COLUMN loan_applications.intake_owner IS
    'STAFF | BORROWER | ANCHOR — who owns completing intake';
