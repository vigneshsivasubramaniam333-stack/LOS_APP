import { AppSectionCard } from '@/components/ui/AppSectionCard'
import { btBadgeClass, type BadgeTone } from '@/components/ui/btStatusUtils'
import type { ApplicationPartyResponse } from '@/types/application'

function str(v: unknown): string {
  return typeof v === 'string' ? v : ''
}

function maskPan(value: string): string {
  if (!value) return '—'
  return value.length <= 4 ? '••••' : `${'•'.repeat(Math.max(0, value.length - 4))}${value.slice(-4)}`
}

function partyStatusTone(status?: string | null): BadgeTone {
  const s = (status ?? '').toUpperCase()
  if (['COMPLETE', 'ESIGN_COMPLETE', 'KYC_COMPLETE', 'SUBMITTED'].includes(s)) return 'green'
  if (s === 'FAILED') return 'red'
  if (['NOT_STARTED', 'DRAFT', ''].includes(s)) return 'gray'
  return 'amber'
}

function StatusBadge({ label, status }: { label: string; status?: string | null }) {
  return (
    <span className={btBadgeClass(partyStatusTone(status))}>
      {label}: {status ? status.replaceAll('_', ' ') : '—'}
    </span>
  )
}

/**
 * Primary + co-applicant roster for an application (`app.parties`). Renders nothing when there are no
 * co-applicants — invoice discounting (and any product without co-applicants configured) never shows this.
 */
export function ApplicationPartiesPanel({ parties }: { parties: ApplicationPartyResponse[] | null | undefined }) {
  const all = parties ?? []
  const hasCoApplicants = all.some((p) => p.role === 'CO_APPLICANT')
  if (!hasCoApplicants) return null

  const ordered = [...all].sort((a, b) => a.sequenceNo - b.sequenceNo)
  const esignTotal = all.length
  const esignDone = all.filter((p) => String(p.esignStatus ?? '').toUpperCase() === 'COMPLETE').length

  return (
    <AppSectionCard
      tone="info"
      className="mb-4"
      title="Applicants"
      subtitle={`${all.length} applicant${all.length === 1 ? '' : 's'} on this application · eSign ${esignDone}/${esignTotal} complete`}
    >
      <div className="space-y-3">
        {ordered.map((p) => {
          const name = p.displayName || str(p.personalInfo?.fullName) || '—'
          const email = p.email || str(p.personalInfo?.email) || '—'
          const mobile = p.mobile || str(p.personalInfo?.mobile) || '—'
          const dob = str(p.personalInfo?.dateOfBirth)
          const pan = str(p.personalInfo?.panNumber) || str(p.personalInfo?.pan)
          const gender = str(p.personalInfo?.gender)
          const occupation = str(p.personalInfo?.occupation)
          return (
            <div key={p.id} className="rounded border border-slate-200 bg-white p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-sm font-medium text-slate-900">{name}</span>
                <span className="bt-tag">{p.role === 'PRIMARY' ? 'Primary' : 'Co-applicant'}</span>
              </div>
              <p className="mt-1 text-xs text-slate-600">
                {email} · {mobile}
              </p>
              {(dob || pan || gender || occupation) ? (
                <p className="mt-1 text-xs text-slate-500">
                  {[dob ? `DOB: ${dob}` : '', gender ? `Gender: ${gender}` : '', occupation ? `Occupation: ${occupation}` : '', pan ? `PAN: ${maskPan(pan)}` : '']
                    .filter(Boolean)
                    .join(' · ')}
                </p>
              ) : null}
              <div className="mt-2 flex flex-wrap gap-2">
                <StatusBadge label="Intake" status={p.intakeStatus} />
                <StatusBadge label="KYC" status={p.kycStatus} />
                <StatusBadge label="eSign" status={p.esignStatus} />
              </div>
            </div>
          )
        })}
      </div>
    </AppSectionCard>
  )
}
