import { buildIntakeReadback } from '@/lib/intake/intakeReadback'
import type { ApplicationResponse } from '@/types/application'

export function BorrowerSubmittedIntakePanel({ app }: { app: ApplicationResponse }) {
  const sections = buildIntakeReadback(app)
  const parties = [...(app.parties ?? [])].sort((a, b) => a.sequenceNo - b.sequenceNo)
  const hasCoApplicants = parties.some((p) => p.role === 'CO_APPLICANT')

  if (sections.length === 0 && !hasCoApplicants) {
    return (
      <p className="text-sm text-slate-600">
        No detailed intake fields are stored on this application yet. They appear here after the applicant completes the
        intake steps (or after staff entry).
      </p>
    )
  }

  function partyRows(party: (typeof parties)[number]) {
    const info = party.personalInfo ?? {}
    const rows: { label: string; value: string }[] = [
      { label: 'Role', value: party.role === 'PRIMARY' ? 'Primary applicant' : 'Co-applicant' },
      { label: 'Name', value: party.displayName || str(info.fullName) || '—' },
      { label: 'Email', value: party.email || str(info.email) || '—' },
      { label: 'Mobile', value: party.mobile || str(info.mobile) || '—' },
      { label: 'Relationship', value: str(info.relationship) || '—' },
      { label: 'Date of birth', value: str(info.dateOfBirth) || '—' },
      { label: 'Gender', value: str(info.gender) || '—' },
      { label: 'Occupation', value: str(info.occupation) || '—' },
      { label: 'PAN', value: str(info.panNumber) || str(info.pan) || '—' },
      { label: 'Aadhaar (last 4)', value: str(info.aadhaarLast4) || '—' },
      { label: 'Address', value: str(info.addressLine1) || str(info.currentAddress) || '—' },
      { label: 'Intake status', value: party.intakeStatus ? party.intakeStatus.replaceAll('_', ' ') : '—' },
      { label: 'KYC status', value: party.kycStatus ? party.kycStatus.replaceAll('_', ' ') : '—' },
      { label: 'eSign status', value: party.esignStatus ? party.esignStatus.replaceAll('_', ' ') : '—' },
    ]
    return rows.filter((r) => r.value && r.value !== '—')
  }

  function str(v: unknown): string {
    return typeof v === 'string' && v.trim() ? v.trim() : ''
  }

  return (
    <div className="space-y-6">
      {hasCoApplicants ? (
        <div className="space-y-4">
          <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Applicants on this file</h3>
          {parties.map((party) => {
            const rows = partyRows(party)
            const title =
              party.role === 'PRIMARY'
                ? `Primary · ${party.displayName || str(party.personalInfo?.fullName) || 'Applicant'}`
                : `Co-applicant · ${party.displayName || str(party.personalInfo?.fullName) || 'Applicant'}`
            return (
              <div key={party.id} className="bt-section-card bt-section-card--info p-4">
                <h3 className="mb-3 bt-section-card__title">{title}</h3>
                <div className="grid gap-2 sm:grid-cols-2">
                  {rows.map((r) => (
                    <div
                      key={r.label}
                      className="rounded-lg border border-slate-100 bg-gradient-to-b from-slate-50 to-white px-3 py-2 shadow-sm"
                    >
                      <div className="text-xs font-medium text-slate-500">{r.label}</div>
                      <div className="mt-0.5 break-words text-sm text-slate-900">{r.value}</div>
                    </div>
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      ) : null}
      {sections.map((sec) => (
        <div key={sec.title} className="bt-section-card bt-section-card--default p-4">
          <h3 className="mb-3 bt-section-card__title">
            {hasCoApplicants ? `Primary application · ${sec.title}` : sec.title}
          </h3>
          <div className="grid gap-2 sm:grid-cols-2">
            {sec.rows.map((r) => (
              <div
                key={r.label}
                className="rounded-lg border border-slate-100 bg-gradient-to-b from-slate-50 to-white px-3 py-2 shadow-sm"
              >
                <div className="text-xs font-medium text-slate-500">{r.label}</div>
                <div className="mt-0.5 break-words text-sm text-slate-900">{r.value}</div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
