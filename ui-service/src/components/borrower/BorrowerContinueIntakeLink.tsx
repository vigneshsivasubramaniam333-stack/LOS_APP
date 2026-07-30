import { Link } from 'react-router-dom'
import type { ApplicationStatus } from '@/types/application'
import { isBorrowerResumableIntakeStatus } from '@/lib/borrowerApplicationDeletable'

export function BorrowerContinueIntakeLink({
  applicationId,
  status,
  partyId,
  partyRole,
  canResumeMyIntake,
  label = 'Continue application',
  className = 'rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white',
}: {
  applicationId: string
  status: ApplicationStatus
  partyId?: string | null
  partyRole?: string | null
  canResumeMyIntake?: boolean | null
  label?: string
  className?: string
}) {
  // Require explicit true so primary applicants waiting on co-applicants do not keep seeing Continue.
  if (canResumeMyIntake !== true || !isBorrowerResumableIntakeStatus(status)) return null
  const partyQuery =
    partyRole === 'CO_APPLICANT' && partyId
      ? `&partyId=${encodeURIComponent(partyId)}`
      : ''
  return (
    <Link to={`/borrower/apply?resume=${applicationId}${partyQuery}`} className={className}>
      {label}
    </Link>
  )
}
