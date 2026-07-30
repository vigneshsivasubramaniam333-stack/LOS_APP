import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getApplication } from '@/api/applications'
import { getBorrowerApplicationDetail } from '@/api/borrowerPortal'
import { uploadDocument } from '@/api/documents'
import { getActiveWorkflow } from '@/api/workflows'
import { listApplicationParties, submitApplicationParty, updateApplicationPartyPersonalInfo } from '@/api/workflow'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { resolveGenderOptions, resolveOccupationOptions } from '@/lib/intake/intakeOptionCatalogs'
import { validateApplicationIdentity } from '@/lib/intake/checkIntakeIdentity'
import { notifyError, notifySuccess } from '@/lib/notify'
import type { ApplicationPartyResponse } from '@/types/application'
import type { WorkflowCoApplicantConfig, WorkflowConfigResponse } from '@/types/workflow'

const SUBMITTED_INTAKE_STATUSES = new Set(['SUBMITTED', 'KYC_COMPLETE', 'ESIGN_PENDING', 'ESIGN_COMPLETE'])

function str(v: unknown): string {
  return typeof v === 'string' ? v : v == null ? '' : String(v)
}

/**
 * Borrower-portal multi-section form for a co-applicant (`?resume={appId}&partyId={partyId}`).
 * Sections mirror primary intake at a lighter depth: basic, identity/KYC, documents, consent.
 */
export function CoApplicantPortal({ applicationId, partyId }: { applicationId: string; partyId: string }) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [party, setParty] = useState<ApplicationPartyResponse | null>(null)
  const [applicationNumber, setApplicationNumber] = useState('')
  const [fullName, setFullName] = useState('')
  const [mobile, setMobile] = useState('')
  const [email, setEmail] = useState('')
  const [dateOfBirth, setDateOfBirth] = useState('')
  const [panNumber, setPanNumber] = useState('')
  const [gender, setGender] = useState('')
  const [occupation, setOccupation] = useState('')
  const [relationship, setRelationship] = useState('')
  const [aadhaarLast4, setAadhaarLast4] = useState('')
  const [addressLine1, setAddressLine1] = useState('')
  const [city, setCity] = useState('')
  const [stateName, setStateName] = useState('')
  const [pincode, setPincode] = useState('')
  const [consent, setConsent] = useState(false)
  const [coApplicantConfig, setCoApplicantConfig] = useState<WorkflowCoApplicantConfig | null>(null)
  const [workflow, setWorkflow] = useState<WorkflowConfigResponse | null>(null)
  const [busy, setBusy] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [uploadedTypes, setUploadedTypes] = useState<string[]>([])
  const [uploadBusyType, setUploadBusyType] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      setLoading(true)
      setError(null)
      try {
        const [app, detail, parties] = await Promise.all([
          getApplication(applicationId).catch(() => null),
          getBorrowerApplicationDetail(applicationId).catch(() => null),
          listApplicationParties(applicationId),
        ])
        const workflow = app
          ? await getActiveWorkflow(
              app.borrowerType,
              app.loanProduct,
              app.intakeSegment ?? 'BORROWER',
            ).catch(() => null)
          : null
        if (cancelled) return
        const p = parties.find((x) => x.id === partyId)
        if (!p) {
          setError('Co-applicant record not found for this application.')
          return
        }
        if (p.role !== 'CO_APPLICANT') {
          setError('This link is not a co-applicant link.')
          return
        }
        setApplicationNumber(detail?.applicationNumber ?? app?.applicationNumber ?? applicationId)
        setParty(p)
        const pi = p.personalInfo ?? {}
        setFullName(p.displayName ?? str(pi.fullName))
        setMobile(p.mobile ?? str(pi.mobile))
        setEmail(p.email ?? str(pi.email))
        setDateOfBirth(str(pi.dateOfBirth))
        setPanNumber(str(pi.panNumber))
        setGender(str(pi.gender))
        setOccupation(str(pi.occupation))
        setRelationship(str(pi.relationship))
        setAadhaarLast4(str(pi.aadhaarLast4))
        setAddressLine1(str(pi.addressLine1) || str(pi.currentAddress))
        setCity(str(pi.city))
        setStateName(str(pi.state))
        setPincode(str(pi.pincode))
        setCoApplicantConfig(workflow?.intakeConfig?.coApplicant ?? null)
        setWorkflow(workflow)
        const priorUploads = Array.isArray(pi.uploadedDocumentTypes)
          ? (pi.uploadedDocumentTypes as unknown[]).map(String)
          : []
        setUploadedTypes(priorUploads)
        setSubmitted(p.intakeStatus !== 'SENT_BACK' && SUBMITTED_INTAKE_STATUSES.has(String(p.intakeStatus ?? '')))
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Could not load your co-applicant details.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [applicationId, partyId])

  async function onUpload(documentType: string, file: File | null) {
    if (!file) return
    setUploadBusyType(documentType)
    setError(null)
    try {
      await uploadDocument(applicationId, file, documentType)
      setUploadedTypes((prev) => (prev.includes(documentType) ? prev : [...prev, documentType]))
      notifySuccess(`${documentType.replaceAll('_', ' ')} uploaded`)
    } catch (e) {
      notifyError(e, 'Document upload failed')
      setError(e instanceof Error ? e.message : 'Document upload failed')
    } finally {
      setUploadBusyType(null)
    }
  }

  async function onSubmit() {
    if (!fullName.trim()) {
      setError('Enter your full name.')
      return
    }
    if (mobile.replace(/\D/g, '').length < 10) {
      setError('Enter a valid mobile number.')
      return
    }
    if (!email.trim()) {
      setError('Enter your email.')
      return
    }
    if (coApplicantConfig?.personalFields?.dateOfBirth?.required && !dateOfBirth.trim()) {
      setError('Enter your date of birth.')
      return
    }
    if (coApplicantConfig?.personalFields?.gender?.required && !gender.trim()) {
      setError('Enter your gender.')
      return
    }
    if (coApplicantConfig?.personalFields?.occupation?.required && !occupation.trim()) {
      setError('Enter your occupation.')
      return
    }
    const requiredDocuments = (coApplicantConfig?.standaloneDocuments ?? []).filter((d) => d.required)
    for (const doc of requiredDocuments) {
      if (!uploadedTypes.includes(doc.documentType)) {
        setError(`Please upload required document: ${doc.label || doc.documentType}`)
        return
      }
    }
    if (!consent) {
      setError('Confirm your consent before submitting.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await validateApplicationIdentity({
        applicationId,
        asCoApplicant: true,
        email: email.trim() || undefined,
        mobile: mobile.replace(/\D/g, '') || undefined,
        panNumber: panNumber.trim() || undefined,
      })
      await updateApplicationPartyPersonalInfo(applicationId, partyId, {
        fullName: fullName.trim(),
        mobile: mobile.trim(),
        email: email.trim(),
        ...(dateOfBirth.trim() ? { dateOfBirth: dateOfBirth.trim() } : {}),
        ...(panNumber.trim() ? { panNumber: panNumber.trim().toUpperCase() } : {}),
        ...(gender.trim() ? { gender: gender.trim() } : {}),
        ...(occupation.trim() ? { occupation: occupation.trim() } : {}),
        ...(relationship.trim() ? { relationship: relationship.trim() } : {}),
        ...(aadhaarLast4.trim() ? { aadhaarLast4: aadhaarLast4.trim() } : {}),
        ...(addressLine1.trim() ? { addressLine1: addressLine1.trim(), currentAddress: addressLine1.trim() } : {}),
        ...(city.trim() ? { city: city.trim() } : {}),
        ...(stateName.trim() ? { state: stateName.trim() } : {}),
        ...(pincode.trim() ? { pincode: pincode.trim() } : {}),
        uploadedDocumentTypes: uploadedTypes,
      })
      await submitApplicationParty(applicationId, partyId)
      setSubmitted(true)
      notifySuccess('Your details have been submitted.')
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Could not submit your details.'
      setError(msg)
      notifyError(e, 'Could not submit your details.')
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return (
      <div className="mx-auto w-full max-w-5xl px-4">
        <LoadingState label="Loading your co-applicant details…" />
      </div>
    )
  }

  if (error && !party) {
    return (
      <div className="mx-auto w-full max-w-5xl px-4">
        <ErrorState message={error} />
      </div>
    )
  }

  if (submitted) {
    return (
      <div className="mx-auto w-full max-w-5xl space-y-4 px-4">
        <h1 className="text-xl font-semibold text-slate-900">Thank you</h1>
        <p className="text-sm text-slate-700">
          Your details for application <span className="font-medium">{applicationNumber}</span> have been submitted.
          The lender will reach out if anything else is needed.
        </p>
        <Link to="/borrower/dashboard" className="text-sm font-medium text-slate-800 underline">
          Go to dashboard
        </Link>
      </div>
    )
  }

  const personalFields = coApplicantConfig?.personalFields
  const collectDob = personalFields?.dateOfBirth?.collect !== false
  const collectGender = personalFields?.gender?.collect !== false
  const collectOccupation = personalFields?.occupation?.collect !== false
  const requiredDocuments = coApplicantConfig?.standaloneDocuments ?? []
  const genderOptions = resolveGenderOptions(workflow)
  const occupationOptions = resolveOccupationOptions(workflow)
  const defaultDocs =
    requiredDocuments.length > 0
      ? requiredDocuments
      : [
          { documentType: 'PAN_CARD', label: 'PAN card', required: false },
          { documentType: 'AADHAAR', label: 'Aadhaar', required: false },
          { documentType: 'PHOTOGRAPH', label: 'Photograph', required: false },
        ]
  const sendBackNotes = str(party?.personalInfo?.sendBackNotes)

  return (
    <div className="mx-auto w-full max-w-5xl space-y-5 px-4 py-2 sm:px-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">You&apos;ve been added as a co-applicant</h1>
        <p className="mt-1 text-sm text-slate-600">
          Application <span className="font-medium">{applicationNumber}</span> — complete each section below. You only
          submit your own information; the primary applicant&apos;s loan details are separate.
        </p>
      </div>
      {error ? <ErrorState message={error} /> : null}
      {party?.intakeStatus === 'SENT_BACK' && sendBackNotes ? (
        <section className="rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
          <h2 className="font-semibold">Changes requested by the lender</h2>
          <p className="mt-1 whitespace-pre-wrap">{sendBackNotes}</p>
        </section>
      ) : null}

      <section className="space-y-4 bt-card p-5 sm:p-6">
        <h2 className="text-base font-semibold text-slate-900">1. Basic details</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <label className="block text-sm text-slate-700 sm:col-span-2 lg:col-span-3">
            <span className="mb-1 block text-xs font-medium text-slate-500">Full name *</span>
            <input className="bt-input w-full" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Mobile *</span>
            <input type="tel" className="bt-input w-full" value={mobile} onChange={(e) => setMobile(e.target.value)} />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Email *</span>
            <input type="email" className="bt-input w-full" value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Relationship to primary</span>
            <input className="bt-input w-full" value={relationship} onChange={(e) => setRelationship(e.target.value)} />
          </label>
          {collectDob ? (
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">
                Date of birth{personalFields?.dateOfBirth?.required ? ' *' : ''}
              </span>
              <input
                type="date"
                className="bt-input w-full"
                value={dateOfBirth}
                onChange={(e) => setDateOfBirth(e.target.value)}
              />
            </label>
          ) : null}
          {collectGender ? (
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">
                Gender{personalFields?.gender?.required ? ' *' : ''}
              </span>
              <select className="bt-input w-full" value={gender} onChange={(e) => setGender(e.target.value)}>
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
              <span className="mb-1 block text-xs font-medium text-slate-500">
                Occupation{personalFields?.occupation?.required ? ' *' : ''}
              </span>
              <select className="bt-input w-full" value={occupation} onChange={(e) => setOccupation(e.target.value)}>
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
              value={addressLine1}
              onChange={(e) => setAddressLine1(e.target.value)}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">City</span>
            <input className="bt-input w-full" value={city} onChange={(e) => setCity(e.target.value)} />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">State</span>
            <input className="bt-input w-full" value={stateName} onChange={(e) => setStateName(e.target.value)} />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Pincode</span>
            <input className="bt-input w-full" value={pincode} onChange={(e) => setPincode(e.target.value)} />
          </label>
        </div>
      </section>

      <section className="space-y-4 bt-card p-5 sm:p-6">
        <h2 className="text-base font-semibold text-slate-900">2. Identity / KYC</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">PAN</span>
            <input
              className="bt-input w-full font-mono uppercase"
              maxLength={10}
              value={panNumber}
              onChange={(e) => setPanNumber(e.target.value.toUpperCase())}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Aadhaar last 4 digits</span>
            <input
              className="bt-input w-full font-mono"
              inputMode="numeric"
              maxLength={4}
              value={aadhaarLast4}
              onChange={(e) => setAadhaarLast4(e.target.value.replace(/\D/g, ''))}
            />
          </label>
        </div>
        <p className="text-xs text-slate-500">
          Full KYC verification (bureau / video KYC) is completed with the lender after submission when required.
        </p>
      </section>

      <section className="space-y-3 bt-card p-5 sm:p-6">
        <h2 className="text-base font-semibold text-slate-900">3. Documents</h2>
        <ul className="grid gap-3 sm:grid-cols-2">
          {defaultDocs.map((document) => {
            const done = uploadedTypes.includes(document.documentType)
            return (
              <li key={document.documentType} className="rounded border border-slate-200 bg-white p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="text-sm font-medium text-slate-900">
                      {document.label || document.documentType}
                      {document.required ? ' *' : ''}
                    </p>
                    <p className="text-xs text-slate-500">{done ? 'Uploaded' : 'Not uploaded yet'}</p>
                  </div>
                  <label className="cursor-pointer rounded-md border border-slate-300 bg-slate-50 px-3 py-1.5 text-xs font-medium text-slate-800">
                    {uploadBusyType === document.documentType ? 'Uploading…' : done ? 'Replace' : 'Upload'}
                    <input
                      type="file"
                      className="hidden"
                      disabled={busy || uploadBusyType != null}
                      onChange={(e) => {
                        const file = e.target.files?.[0] ?? null
                        e.target.value = ''
                        void onUpload(document.documentType, file)
                      }}
                    />
                  </label>
                </div>
              </li>
            )
          })}
        </ul>
      </section>

      <section className="bt-card p-5 sm:p-6">
        <h2 className="text-base font-semibold text-slate-900">4. Consent</h2>
        <label className="mt-3 flex items-start gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
          <span>I confirm the information is accurate and I consent to credit checks / being a co-applicant.</span>
        </label>
      </section>

      <button
        type="button"
        onClick={() => void onSubmit()}
        disabled={busy}
        className="rounded-md bg-slate-900 px-5 py-2.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
      >
        {busy ? 'Submitting…' : 'Submit my details'}
      </button>
    </div>
  )
}
