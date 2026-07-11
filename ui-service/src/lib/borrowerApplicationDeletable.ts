import type { ApplicationStatus } from '@/types/application'

/** Drafts the borrower may delete from the portal (not yet submitted to processing). */
export function isBorrowerDeletableApplicationStatus(s: ApplicationStatus): boolean {
  return s === 'DRAFT' || s === 'CONSENT_PENDING'
}

/** Applications the borrower can resume in the intake wizard. */
export function isBorrowerResumableIntakeStatus(s: ApplicationStatus): boolean {
  return s === 'DRAFT' || s === 'CONSENT_PENDING' || s === 'BORROWER_SENT_BACK'
}

export function isDelegatedBorrowerIntake(app: {
  status: ApplicationStatus
  intakeOwner?: string | null
}): boolean {
  return (
    app.intakeOwner === 'BORROWER' &&
    (app.status === 'CONSENT_PENDING' || app.status === 'BORROWER_SENT_BACK')
  )
}
