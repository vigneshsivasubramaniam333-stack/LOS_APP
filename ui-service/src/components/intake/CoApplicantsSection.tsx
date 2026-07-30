import { uploadDocument } from '@/api/documents'
import type { IntakeCodedOption } from '@/lib/intake/intakeOptionCatalogs'
import { DEFAULT_GENDER_OPTIONS, DEFAULT_OCCUPATION_OPTIONS } from '@/lib/intake/intakeOptionCatalogs'
import { notifyError, notifySuccess } from '@/lib/notify'

/** Staff intake: add / remove co-applicant rows when the active workflow enables joint applications. */
export interface CoApplicantRow {
  /** Existing `ApplicationParty` id when this co-applicant was already saved. */
  id?: string
  fullName: string
  mobile: string
  email: string
  relationship: string
  /** Extended fields when RM fills co-applicant details themselves. */
  dateOfBirth?: string
  gender?: string
  occupation?: string
  panNumber?: string
  aadhaarLast4?: string
  voterId?: string
  dlNumber?: string
  bankAccountNumber?: string
  ifscCode?: string
  bankName?: string
  addressLine1?: string
  city?: string
  state?: string
  pincode?: string
  consentAccepted?: boolean
  uploadedDocumentTypes?: string[]
}

export type StaffMultiPartyPath = 'notify' | 'staff_fill'

function newCoApplicantRow(): CoApplicantRow {
  return { fullName: '', mobile: '', email: '', relationship: '', uploadedDocumentTypes: [] }
}

export function CoApplicantsSection({
  coApplicants,
  onChange,
  min,
  max,
  completionPath,
  onCompletionPathChange,
  showCompletionPathChooser = false,
}: {
  coApplicants: CoApplicantRow[]
  onChange: (rows: CoApplicantRow[]) => void
  min: number
  max: number
  completionPath?: StaffMultiPartyPath | null
  onCompletionPathChange?: (path: StaffMultiPartyPath) => void
  showCompletionPathChooser?: boolean
}) {
  function updateRow(idx: number, patch: Partial<CoApplicantRow>) {
    const next = [...coApplicants]
    next[idx] = { ...next[idx]!, ...patch }
    onChange(next)
  }

  function removeRow(idx: number) {
    onChange(coApplicants.filter((_, i) => i !== idx))
  }

  return (
    <section className="space-y-4 bt-card p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="bt-card-title">Co-applicants</h2>
        <button
          type="button"
          className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
          onClick={() => onChange([...coApplicants, newCoApplicantRow()])}
          disabled={coApplicants.length >= max}
        >
          Add co-applicant
        </button>
      </div>
      <p className="text-xs text-slate-500">
        {min > 0 ? `At least ${min} co-applicant(s) required. ` : ''}
        Up to {max} co-applicant(s) allowed. Email and mobile must be different for every applicant (including the
        primary borrower).
      </p>
      {coApplicants.length === 0 ? (
        <p className="rounded border border-dashed border-slate-300 bg-slate-50 p-3 text-sm text-slate-600">
          No co-applicants added yet.
        </p>
      ) : null}
      <div className="space-y-3">
        {coApplicants.map((row, idx) => (
          <div key={row.id ?? idx} className="rounded-lg border border-slate-200 bg-white p-3">
            <div className="mb-2 flex items-center justify-between">
              <p className="text-xs font-medium uppercase text-slate-500">Co-applicant {idx + 1}</p>
              <button type="button" className="text-xs text-rose-700" onClick={() => removeRow(idx)}>
                Remove
              </button>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Full name *</span>
                <input
                  className="bt-input w-full"
                  value={row.fullName}
                  onChange={(e) => updateRow(idx, { fullName: e.target.value })}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Mobile *</span>
                <input
                  type="tel"
                  className="bt-input w-full"
                  value={row.mobile}
                  onChange={(e) => updateRow(idx, { mobile: e.target.value })}
                />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Email *</span>
                <input
                  type="email"
                  className="bt-input w-full"
                  value={row.email}
                  onChange={(e) => updateRow(idx, { email: e.target.value })}
                />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Relationship</span>
                <input
                  className="bt-input w-full"
                  value={row.relationship}
                  onChange={(e) => updateRow(idx, { relationship: e.target.value })}
                />
              </label>
            </div>
          </div>
        ))}
      </div>
      {showCompletionPathChooser && coApplicants.length > 0 && onCompletionPathChange ? (
        <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-600">How should applicants complete intake?</p>
          <label
            className={`flex cursor-pointer items-start gap-2 rounded border px-3 py-2 ${
              completionPath === 'notify' ? 'border-indigo-400 bg-indigo-50' : 'border-slate-200 bg-white'
            }`}
          >
            <input
              type="radio"
              className="mt-1"
              checked={completionPath === 'notify'}
              onChange={() => onCompletionPathChange('notify')}
            />
            <span className="text-sm text-slate-800">
              <span className="font-medium">Notify applicants (portal invite)</span>
              <span className="mt-0.5 block text-xs text-slate-500">
                Only name, email, and mobile are required here. Applicants complete the rest in the borrower portal.
              </span>
            </span>
          </label>
          <label
            className={`flex cursor-pointer items-start gap-2 rounded border px-3 py-2 ${
              completionPath === 'staff_fill' ? 'border-indigo-400 bg-indigo-50' : 'border-slate-200 bg-white'
            }`}
          >
            <input
              type="radio"
              className="mt-1"
              checked={completionPath === 'staff_fill'}
              onChange={() => onCompletionPathChange('staff_fill')}
            />
            <span className="text-sm text-slate-800">
              <span className="font-medium">I will fill all applicant details</span>
              <span className="mt-0.5 block text-xs text-slate-500">
                After the primary review step, enter each co-applicant&apos;s full details (identity, documents, consent).
              </span>
            </span>
          </label>
          {!completionPath ? (
            <p className="text-xs font-medium text-amber-800">Select a completion path to enable Continue / Notify.</p>
          ) : null}
        </div>
      ) : null}
    </section>
  )
}

const DEFAULT_STAFF_DOCS = [
  { documentType: 'PAN_CARD', label: 'PAN card' },
  { documentType: 'AADHAAR', label: 'Aadhaar' },
  { documentType: 'PHOTOGRAPH', label: 'Photograph' },
]

/** Staff-only form to capture one co-applicant's extended details (staff_fill path). */
export function StaffCoApplicantDetailForm({
  index,
  total,
  row,
  onChange,
  applicationId,
  collectDob = true,
  collectGender = true,
  collectOccupation = true,
  requireDob = false,
  genderOptions = DEFAULT_GENDER_OPTIONS,
  occupationOptions = DEFAULT_OCCUPATION_OPTIONS,
  documentTypes = DEFAULT_STAFF_DOCS,
}: {
  index: number
  total: number
  row: CoApplicantRow
  onChange: (patch: Partial<CoApplicantRow>) => void
  applicationId?: string | null
  collectDob?: boolean
  collectGender?: boolean
  collectOccupation?: boolean
  requireDob?: boolean
  genderOptions?: IntakeCodedOption[]
  occupationOptions?: IntakeCodedOption[]
  documentTypes?: { documentType: string; label: string }[]
}) {
  const uploaded = row.uploadedDocumentTypes ?? []

  async function onUpload(documentType: string, file: File | null) {
    if (!file || !applicationId) return
    try {
      await uploadDocument(applicationId, file, documentType)
      onChange({
        uploadedDocumentTypes: uploaded.includes(documentType) ? uploaded : [...uploaded, documentType],
      })
      notifySuccess(`${documentType.replaceAll('_', ' ')} uploaded`)
    } catch (e) {
      notifyError(e, 'Document upload failed')
    }
  }

  return (
    <section className="mx-auto w-full max-w-5xl space-y-4">
      <div className="bt-card space-y-4 p-5">
        <div>
          <h2 className="bt-card-title">
            Co-applicant {index + 1} of {total}
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            Enter full details for this co-applicant. Contact details must stay different from the primary borrower and
            other co-applicants.
          </p>
        </div>

        <div>
          <h3 className="mb-3 text-sm font-semibold text-slate-900">1. Basic details</h3>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Full name *</span>
              <input
                className="bt-input w-full"
                value={row.fullName}
                onChange={(e) => onChange({ fullName: e.target.value })}
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Mobile *</span>
              <input
                type="tel"
                className="bt-input w-full"
                value={row.mobile}
                onChange={(e) => onChange({ mobile: e.target.value })}
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Email *</span>
              <input
                type="email"
                className="bt-input w-full"
                value={row.email}
                onChange={(e) => onChange({ email: e.target.value })}
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Relationship</span>
              <input
                className="bt-input w-full"
                value={row.relationship}
                onChange={(e) => onChange({ relationship: e.target.value })}
              />
            </label>
            {collectDob ? (
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">
                  Date of birth{requireDob ? ' *' : ''}
                </span>
                <input
                  type="date"
                  className="bt-input w-full"
                  value={row.dateOfBirth ?? ''}
                  onChange={(e) => onChange({ dateOfBirth: e.target.value })}
                />
              </label>
            ) : null}
            {collectGender ? (
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Gender</span>
                <select
                  className="bt-input w-full"
                  value={row.gender ?? ''}
                  onChange={(e) => onChange({ gender: e.target.value })}
                >
                  <option value="">— Select gender —</option>
                  {genderOptions.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            {collectOccupation ? (
              <label className="block text-sm text-slate-700 sm:col-span-2 lg:col-span-3">
                <span className="mb-1 block text-xs font-medium text-slate-500">Occupation</span>
                <select
                  className="bt-input w-full"
                  value={row.occupation ?? ''}
                  onChange={(e) => onChange({ occupation: e.target.value })}
                >
                  <option value="">— Select occupation —</option>
                  {occupationOptions.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            <label className="block text-sm text-slate-700 sm:col-span-2 lg:col-span-3">
              <span className="mb-1 block text-xs font-medium text-slate-500">Current address</span>
              <input
                className="bt-input w-full"
                value={row.addressLine1 ?? ''}
                onChange={(e) => onChange({ addressLine1: e.target.value })}
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">City</span>
              <input
                className="bt-input w-full"
                value={row.city ?? ''}
                onChange={(e) => onChange({ city: e.target.value })}
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">State</span>
              <input
                className="bt-input w-full"
                value={row.state ?? ''}
                onChange={(e) => onChange({ state: e.target.value })}
              />
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Pincode</span>
              <input
                className="bt-input w-full"
                value={row.pincode ?? ''}
                onChange={(e) => onChange({ pincode: e.target.value })}
              />
            </label>
          </div>
        </div>
      </div>

      <div className="bt-card space-y-4 p-5">
        <h3 className="text-sm font-semibold text-slate-900">2. Identity / KYC</h3>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">PAN</span>
            <input
              className="bt-input w-full uppercase font-mono"
              maxLength={10}
              value={row.panNumber ?? ''}
              onChange={(e) => onChange({ panNumber: e.target.value.toUpperCase() })}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Aadhaar last 4 digits</span>
            <input
              className="bt-input w-full font-mono"
              inputMode="numeric"
              maxLength={4}
              value={row.aadhaarLast4 ?? ''}
              onChange={(e) => onChange({ aadhaarLast4: e.target.value.replace(/\D/g, '') })}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Voter ID (EPIC)</span>
            <input
              className="bt-input w-full uppercase"
              value={row.voterId ?? ''}
              onChange={(e) => onChange({ voterId: e.target.value.toUpperCase() })}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Driving licence</span>
            <input
              className="bt-input w-full uppercase"
              value={row.dlNumber ?? ''}
              onChange={(e) => onChange({ dlNumber: e.target.value.toUpperCase() })}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Bank account number</span>
            <input
              className="bt-input w-full font-mono"
              value={row.bankAccountNumber ?? ''}
              onChange={(e) => onChange({ bankAccountNumber: e.target.value })}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">IFSC</span>
            <input
              className="bt-input w-full font-mono uppercase"
              maxLength={11}
              value={row.ifscCode ?? ''}
              onChange={(e) => onChange({ ifscCode: e.target.value.toUpperCase() })}
            />
          </label>
          <label className="block text-sm text-slate-700 sm:col-span-2 lg:col-span-3">
            <span className="mb-1 block text-xs font-medium text-slate-500">Bank name</span>
            <input
              className="bt-input w-full"
              value={row.bankName ?? ''}
              onChange={(e) => onChange({ bankName: e.target.value })}
            />
          </label>
        </div>
      </div>

      <div className="bt-card space-y-3 p-5">
        <h3 className="text-sm font-semibold text-slate-900">3. Documents</h3>
        {!applicationId ? (
          <p className="text-sm text-amber-800">Save the application first to enable document uploads.</p>
        ) : (
          <ul className="space-y-2">
            {documentTypes.map((document) => {
              const done = uploaded.includes(document.documentType)
              return (
                <li
                  key={document.documentType}
                  className="flex flex-wrap items-center justify-between gap-2 rounded border border-slate-200 bg-white px-3 py-2"
                >
                  <div>
                    <p className="text-sm font-medium text-slate-900">{document.label}</p>
                    <p className="text-xs text-slate-500">{done ? 'Uploaded' : 'Not uploaded yet'}</p>
                  </div>
                  <label className="cursor-pointer rounded-md border border-slate-300 bg-slate-50 px-3 py-1.5 text-xs font-medium text-slate-800">
                    {done ? 'Replace' : 'Upload'}
                    <input
                      type="file"
                      className="hidden"
                      onChange={(e) => {
                        const file = e.target.files?.[0] ?? null
                        e.target.value = ''
                        void onUpload(document.documentType, file)
                      }}
                    />
                  </label>
                </li>
              )
            })}
          </ul>
        )}
      </div>

      <div className="bt-card p-5">
        <h3 className="text-sm font-semibold text-slate-900">4. Consent</h3>
        <label className="mt-3 flex items-start gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={row.consentAccepted === true}
            onChange={(e) => onChange({ consentAccepted: e.target.checked })}
          />
          <span>
            I confirm the co-applicant information is accurate and the applicant consents to credit checks / being a
            co-applicant.
          </span>
        </label>
      </div>
    </section>
  )
}
