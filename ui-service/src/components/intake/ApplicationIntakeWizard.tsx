import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { canAccessAdminConfigNav, canCreateOrNotifyBorrowerIntake } from '@/auth/types'
import { createApplication, getApplication, updateApplication } from '@/api/applications'
import { listBorrowerApplications, type BorrowerAppSummary } from '@/api/borrowerPortal'
import { listDocuments, uploadDocument } from '@/api/documents'
import { listWorkflows } from '@/api/workflows'
import { submitApplicationForKyc } from '@/api/flow'
import { IntakeFieldError } from '@/components/intake/IntakeFieldError'
import { ErrorState } from '@/components/ErrorState'
import { AnchorIntakeWizard } from '@/components/intake/AnchorIntakeWizard'
import { InvoiceOnboardingTypeCards } from '@/components/intake/InvoiceOnboardingTypeCards'
import { PageHeader } from '@/components/PageHeader'
import { consentHelper, pageDescription, pageTitle } from '@/lib/intake/intakeLabels'
import { CollateralIntakeFields } from '@/components/intake/CollateralIntakeFields'
import { IndiaStateCityPincodeFields } from '@/components/intake/IndiaStateCityPincodeFields'
import { persistBorrowerIntakeCollateral } from '@/lib/intake/collateralPersist'
import { buildConsentUpdate, buildIntakeBorrowerUpdate, buildIntakeCreateRequest, buildKycUpdate } from '@/lib/intake/intakePayloads'
import { allDocumentSlotsForIntake } from '@/lib/intake/intakeDocumentSlots'
import { prefetchIntakeGeoForValidation } from '@/lib/intake/masterGeoClientCache'
import { BORROWER_TYPES, createEmptyIntakeFormState, isBusinessBorrowerType, type IntakeFormState, type IntakeMode } from '@/lib/intake/intakeTypes'
import { detectSecuredCollateralKind, requiresCollateral } from '@/lib/intake/securedProducts'
import {
  allConsentsChecked,
  collateralDocumentMissingWarning,
  missingIntakeDocumentTypes,
  productsForBorrowerType,
  validateBorrowerStep,
  validateCollateralIntakeStep,
  validateConsentStep,
  validateKycStep,
  validateNotifyBasics,
  validateProductStep,
} from '@/lib/intake/intakeValidation'
import { BORROWER_TYPE_LABELS } from '@/catalog/borrowerTypes'
import { isInvoiceDiscountingProduct } from '@/catalog/loanProducts'
import {
  DEFAULT_LMS_PRODUCT_CODE,
  DEFAULT_LMS_TENURE_UNIT,
  lmsTenureUnitLabel,
  tenureMagnitudeShortUnit,
} from '@/catalog/lmsTenureUnits'
import { LmsWorkflowConfigReadonly } from '@/components/intake/LmsWorkflowConfigReadonly'
import { linkApplicationToProgram } from '@/api/plp'
import { SelectAnchorProgramStep } from '@/components/intake/SelectAnchorProgramStep'
import { LinkedAnchorProgramReadonly } from '@/components/intake/LinkedAnchorProgramReadonly'
import { InvoiceDiscountingVintageFields } from '@/components/intake/InvoiceDiscountingVintageFields'
import { buildStaffStepLabels, staffIntakeStepIndices } from '@/lib/intake/staffIntakeSteps'
import {
  checkBorrowerIdentity,
  checkKycIdentity,
  intakeErrorMessage,
  intakeStepForDuplicateField,
  validateApplicationIdentity,
} from '@/lib/intake/checkIntakeIdentity'
import { duplicateFieldErrors, duplicateFieldFromError } from '@/lib/userFriendlyError'
import { notifyError, notifySuccess } from '@/lib/notify'
import {
  listApplicationParties,
  notifyBorrowerToComplete,
  submitApplicationParty,
  submitDelegatedBorrowerIntake,
  updateApplicationPartyPersonalInfo,
  upsertApplicationParties,
} from '@/api/workflow'
import {
  CoApplicantsSection,
  StaffCoApplicantDetailForm,
  type CoApplicantRow,
  type StaffMultiPartyPath,
} from '@/components/intake/CoApplicantsSection'
import { CoApplicantPortal } from '@/components/intake/CoApplicantPortal'
import { activeCatalogHasSecuredProduct, productsForIntakeSegment, uniqueActiveWorkflowLoanProducts, workflowLoanProductDisplayName } from '@/utils/workflowProducts'
import { hydrateIntakeFormFromApplication } from '@/lib/intake/hydrateIntakeFromApplication'
import {
  applyHydratedIntakeDefaults,
  inferFirstIncompleteIntakeStep,
  staffCanContinueIntake,
} from '@/lib/intake/intakeResume'
import {
  isBorrowerResumableIntakeStatus,
  isDelegatedBorrowerIntake,
} from '@/lib/borrowerApplicationDeletable'
import {
  resolveAllowedStates,
  resolveCoApplicantConfig,
  resolveDocumentSlots,
  shouldCollectLoanPurposeField,
  shouldCollectPersonalField,
  shouldShowKycIntakeField,
} from '@/lib/workflow/workflowIntakeRules'
import {
  labelForLoanPurpose,
  labelForOccupation,
  resolveGenderOptions,
  resolveLoanPurposeOptions,
  resolveOccupationOptions,
} from '@/lib/intake/intakeOptionCatalogs'
import { IntakeTenureField } from '@/components/intake/IntakeTenureField'
import { ANCHOR_BORROWER_TYPE } from '@/lib/intake/anchorIntakeConstants'
import type { WorkflowConfigResponse } from '@/types/workflow'
import type { BorrowerType } from '@/types/createApplication'
import type { ApplicationStatus } from '@/types/application'

function Stepper({ step, labels }: { step: number; labels: readonly string[] }) {
  return (
    <ol className="mb-8 flex flex-wrap items-center gap-2 border-b border-slate-200 pb-4 text-sm">
      {labels.map((label, i) => (
        <li key={label} className="flex items-center gap-2">
          <span
            className={[
              'flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
              i < step
                ? 'bg-[var(--bt-green-bg)] text-[var(--bt-green)]'
                : i === step
                  ? 'bg-[var(--bt-orange)] text-white'
                  : 'bg-[var(--bt-gray-100)] text-[var(--bt-gray-500)]',
            ].join(' ')}
            aria-current={i === step ? 'step' : undefined}
          >
            {i + 1}
          </span>
          <span className={i === step ? 'font-medium text-slate-900' : 'text-slate-600'}>{label}</span>
          {i < labels.length - 1 ? <span className="hidden sm:inline text-slate-300">·</span> : null}
        </li>
      ))}
    </ol>
  )
}

export interface ApplicationIntakeWizardProps {
  mode: IntakeMode
  /** Lender / sales pages use the staff header + back link; borrower layout uses its own shell. */
  variant: 'borrower' | 'staff'
  /** Staff: resume intake on an existing editable application (`/applications/:id/intake`). */
  editApplicationId?: string
}

/** Statuses where RM is editing an already-submitted case — save changes, do not re-submit for KYC. */
function isStaffPostSubmitEditStatus(status: ApplicationStatus | null): boolean {
  return status === 'BORROWER_SUBMITTED' || status === 'SENT_BACK_TO_RM'
}

export function ApplicationIntakeWizard({ mode, variant, editApplicationId }: ApplicationIntakeWizardProps) {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { user } = useAuth()
  const [step, setStep] = useState(0)
  const [form, setForm] = useState<IntakeFormState>(createEmptyIntakeFormState)
  const [applicationId, setApplicationId] = useState<string | null>(null)
  const [activeWorkflows, setActiveWorkflows] = useState<WorkflowConfigResponse[]>([])
  const [workflowsState, setWorkflowsState] = useState<'loading' | 'ok' | 'err'>('loading')
  const [workflowsError, setWorkflowsError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [docWarning, setDocWarning] = useState<string | null>(null)
  const [collateralDocWarn, setCollateralDocWarn] = useState<string | null>(null)
  /** Staff: after invoice discounting → Anchor, branch into embedded anchor wizard. */
  const [anchorBranch, setAnchorBranch] = useState<{
    requestedAmount: string
    tenureMonths: string
  } | null>(null)
  const [delegatedApp, setDelegatedApp] = useState(false)
  const [sentBackNotes, setSentBackNotes] = useState<string | null>(null)
  const [hydrating, setHydrating] = useState(false)
  const [resumeError, setResumeError] = useState<string | null>(null)
  const [incompleteServer, setIncompleteServer] = useState<BorrowerAppSummary[]>([])
  const [resumeLoaded, setResumeLoaded] = useState(false)
  const [resumedAppStatus, setResumedAppStatus] = useState<ApplicationStatus | null>(null)
  const [coApplicants, setCoApplicants] = useState<CoApplicantRow[]>([])
  const [staffMultiPartyPath, setStaffMultiPartyPath] = useState<StaffMultiPartyPath | null>(null)
  const [staffCoFillIndex, setStaffCoFillIndex] = useState<number | null>(null)

  const resumeApplicationId =
    editApplicationId ?? (variant === 'borrower' ? searchParams.get('resume') : null)
  const coApplicantPartyId = variant === 'borrower' ? searchParams.get('partyId') : null

  function clearFieldError(key: string) {
    setFieldErrors((prev) => {
      if (!prev[key]) return prev
      const next = { ...prev }
      delete next[key]
      return next
    })
  }

  const needColl = useMemo(() => requiresCollateral(form.loanProduct), [form.loanProduct])
  const needPlpAnchorStep = useMemo(
    () =>
      variant === 'staff' &&
      isInvoiceDiscountingProduct(form.loanProduct) &&
      form.invoiceOnboardingChoice === 'BORROWER',
    [variant, form.loanProduct, form.invoiceOnboardingChoice],
  )
  const steps = useMemo(
    () => staffIntakeStepIndices(needColl, needPlpAnchorStep),
    [needColl, needPlpAnchorStep],
  )
  const stepLabels = useMemo(
    () => buildStaffStepLabels(needColl, needPlpAnchorStep),
    [needColl, needPlpAnchorStep],
  )
  const lastStep = steps.last

  const productsForType = productsForBorrowerType(activeWorkflows, form.borrowerType)
  const staffProductList = useMemo(() => uniqueActiveWorkflowLoanProducts(activeWorkflows), [activeWorkflows])
  const borrowerWorkflow = productsForType.find((w) => w.loanProduct === form.loanProduct) ?? null
  /** When Anchor onboarding is chosen, tenure / intake rules must come from the ANCHOR workflow row. */
  const selectedWorkflow = useMemo(() => {
    if (
      isInvoiceDiscountingProduct(form.loanProduct) &&
      form.invoiceOnboardingChoice === 'ANCHOR'
    ) {
      return (
        productsForIntakeSegment(activeWorkflows, 'ANCHOR', ANCHOR_BORROWER_TYPE).find(
          (w) => w.loanProduct === form.loanProduct,
        ) ?? null
      )
    }
    return borrowerWorkflow
  }, [
    activeWorkflows,
    borrowerWorkflow,
    form.invoiceOnboardingChoice,
    form.loanProduct,
  ])
  const allowedStateNames = useMemo(() => resolveAllowedStates(selectedWorkflow), [selectedWorkflow])
  const documentSlots = useMemo(
    () =>
      selectedWorkflow?.intakeConfig?.policy === 'WORKFLOW_DRIVEN'
        ? resolveDocumentSlots(selectedWorkflow, form.borrowerType)
        : allDocumentSlotsForIntake(form),
    [selectedWorkflow, form],
  )
  const productLocked = Boolean(applicationId)
  const coApplicantConfig =
    variant === 'staff' && !isInvoiceDiscountingProduct(form.loanProduct)
      ? resolveCoApplicantConfig(selectedWorkflow)
      : null
  const coApplicantMin = coApplicantConfig?.minCoApplicants ?? 0
  const coApplicantMax = coApplicantConfig?.maxCoApplicants ?? 3
  const staffCanCreateOrNotify =
    variant === 'staff' && canCreateOrNotifyBorrowerIntake(user?.role ?? '')
  const notifyBasicsOk =
    staffCanCreateOrNotify &&
    !validateNotifyBasics(form, mode, activeWorkflows, { needPlpProgram: needPlpAnchorStep })

  useEffect(() => {
    if (!selectedWorkflow || isInvoiceDiscountingProduct(form.loanProduct)) return
    setForm((f) => ({
      ...f,
      lmsProductCode: selectedWorkflow.lmsProductCode?.trim() || DEFAULT_LMS_PRODUCT_CODE,
      lmsTenureUnit: selectedWorkflow.lmsTenureUnit?.trim() || DEFAULT_LMS_TENURE_UNIT,
    }))
  }, [selectedWorkflow?.id, selectedWorkflow?.lmsProductCode, selectedWorkflow?.lmsTenureUnit, form.loanProduct])

  const loadWorkflows = useCallback(async () => {
    setWorkflowsState('loading')
    setWorkflowsError(null)
    try {
      const all = await listWorkflows()
      const act = all.filter((w) => w.active)
      setActiveWorkflows(act)
      setWorkflowsState('ok')
      setForm((f) => {
        const unique = uniqueActiveWorkflowLoanProducts(act)
        if (unique.length === 0) {
          return f.loanProduct ? { ...f, loanProduct: '', invoiceOnboardingChoice: '' } : f
        }
        let loanProduct = f.loanProduct
        if (!loanProduct || !unique.includes(loanProduct)) {
          loanProduct = unique[0]!
        }
        let invoiceOnboardingChoice = f.invoiceOnboardingChoice
        if (!isInvoiceDiscountingProduct(loanProduct)) {
          invoiceOnboardingChoice = ''
        }
        return { ...f, loanProduct, invoiceOnboardingChoice }
      })
    } catch (e) {
      setActiveWorkflows([])
      setWorkflowsError(e instanceof Error ? e.message : 'Failed to load workflows')
      setWorkflowsState('err')
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async listWorkflows; state set inside loadWorkflows
    void loadWorkflows()
  }, [loadWorkflows])

  useEffect(() => {
    if (mode === 'SALES_ASSISTED' || mode === 'ADMIN_INTERNAL') {
      if (!user) return
      // eslint-disable-next-line react-hooks/set-state-in-effect -- optional staff field seed when session appears
      setForm((f) => {
        if (f.salesOfficerName.trim() && f.salesOfficerId.trim()) return f
        return { ...f, salesOfficerName: user.name, salesOfficerId: user.userId }
      })
    }
  }, [mode, user])

  const reloadIncomplete = useCallback(() => {
    if (variant !== 'borrower' || !user) return
    void listBorrowerApplications(0, 40)
      .then((p) => {
        setIncompleteServer(p.content.filter(
          (a) => a.canResumeMyIntake !== false && isBorrowerResumableIntakeStatus(a.status),
        ))
      })
      .catch(() => setIncompleteServer([]))
  }, [variant, user])

  useEffect(() => {
    reloadIncomplete()
  }, [reloadIncomplete])

  useEffect(() => {
    if (variant !== 'borrower' || !user) return
    // eslint-disable-next-line react-hooks/set-state-in-effect -- seed name/email from session when empty
    setForm((f) => {
      if (f.email.trim() && f.fullName.trim()) return f
      return { ...f, fullName: f.fullName || user.name, email: f.email || user.email }
    })
  }, [variant, user])

  useEffect(() => {
    setResumeLoaded(false)
    setResumedAppStatus(null)
  }, [resumeApplicationId])

  useEffect(() => {
    if (!resumeApplicationId || resumeLoaded || workflowsState !== 'ok' || coApplicantPartyId) return
    let cancelled = false
    void (async () => {
      setResumeError(null)
      setHydrating(true)
      try {
        if (variant === 'borrower' && user?.userId) {
          try {
            const listed = await listBorrowerApplications(0, 50)
            const mine = listed.content.find((a) => a.applicationId === resumeApplicationId)
            if (mine?.canResumeMyIntake === false) {
              setResumeError(
                mine.viewerFriendlyStatus
                  ?? 'This application is waiting for another applicant to complete their intake.',
              )
              return
            }
            if (mine?.partyRole === 'CO_APPLICANT' && mine.partyId) {
              if (cancelled) return
              setSearchParams((prev) => {
                const next = new URLSearchParams(prev)
                next.set('resume', resumeApplicationId)
                next.set('partyId', mine.partyId!)
                return next
              })
              setHydrating(false)
              return
            }
          } catch {
            // Fall through to the primary-applicant ownership check.
          }
        }
        const app = await getApplication(resumeApplicationId)
        if (cancelled) return
        if (variant === 'borrower') {
          if (!user || app.customerId !== user.userId) {
            setResumeError('You can only open your own application.')
            return
          }
          if (!isBorrowerResumableIntakeStatus(app.status)) {
            setResumeError('This application is no longer a draft. Open it from your applications list.')
            return
          }
        } else if (editApplicationId) {
          if (!staffCanContinueIntake(app, user?.role)) {
            setResumeError(
              'This application cannot be edited in Continue intake. Open it from the applications list, or wait until it is with the Relationship Manager again.',
            )
            return
          }
        }
        setResumedAppStatus(app.status)
        const h0 = hydrateIntakeFormFromApplication(app)
        let h = applyHydratedIntakeDefaults(h0, app, variant)
        try {
          const docs = await listDocuments(app.id)
          const uploaded = { ...h.documentUploaded }
          for (const d of docs) {
            uploaded[d.documentType] = true
          }
          h = { ...h, documentUploaded: uploaded }
        } catch {
          // optional — document flags improve resume step inference
        }
        setForm(h)
        setApplicationId(app.id)
        setDelegatedApp(isDelegatedBorrowerIntake(app))
        setSentBackNotes(app.borrowerSentBackNotes ?? null)
        const needPlpResume =
          variant === 'staff' &&
          isInvoiceDiscountingProduct(h.loanProduct) &&
          h.invoiceOnboardingChoice === 'BORROWER'
        const needCollResume = requiresCollateral(h.loanProduct)
        const stepMap = staffIntakeStepIndices(needCollResume, needPlpResume)
        setStep(
          inferFirstIncompleteIntakeStep(
            h,
            stepMap,
            mode,
            activeWorkflows,
            needPlpResume,
            needCollResume,
          ),
        )
        if (variant === 'borrower' && !editApplicationId) {
          setSearchParams(
            (prev) => {
              const next = new URLSearchParams(prev)
              next.delete('resume')
              return next
            },
            { replace: true },
          )
        }
      } catch (e) {
        if (!cancelled) setResumeError(intakeErrorMessage(e, 'Could not load application'))
      } finally {
        if (!cancelled) {
          setHydrating(false)
          setResumeLoaded(true)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [resumeApplicationId, resumeLoaded, workflowsState, variant, user, editApplicationId, setSearchParams, coApplicantPartyId])

  useEffect(() => {
    if (!applicationId || !coApplicantConfig) return
    let cancelled = false
    void listApplicationParties(applicationId)
      .then((parties) => {
        if (cancelled) return
        setCoApplicants(
          parties
            .filter((party) => party.role === 'CO_APPLICANT')
            .map((party) => ({
              id: party.id,
              fullName: party.displayName ?? String(party.personalInfo?.fullName ?? ''),
              mobile: party.mobile ?? String(party.personalInfo?.mobile ?? ''),
              email: party.email ?? String(party.personalInfo?.email ?? ''),
              relationship: String(party.personalInfo?.relationship ?? ''),
              dateOfBirth: String(party.personalInfo?.dateOfBirth ?? ''),
              gender: String(party.personalInfo?.gender ?? ''),
              occupation: String(party.personalInfo?.occupation ?? ''),
              panNumber: String(party.personalInfo?.panNumber ?? ''),
              aadhaarLast4: String(party.personalInfo?.aadhaarLast4 ?? ''),
              voterId: String(party.personalInfo?.voterId ?? party.personalInfo?.epicNo ?? ''),
              dlNumber: String(party.personalInfo?.dlNumber ?? party.personalInfo?.dlNo ?? ''),
              bankAccountNumber: String(
                party.personalInfo?.bankAccountNumber ?? party.personalInfo?.accountNumber ?? '',
              ),
              ifscCode: String(party.personalInfo?.ifscCode ?? party.personalInfo?.ifsc ?? ''),
              bankName: String(party.personalInfo?.bankName ?? ''),
              addressLine1: String(
                party.personalInfo?.addressLine1 ?? party.personalInfo?.currentAddress ?? '',
              ),
              city: String(party.personalInfo?.city ?? ''),
              state: String(party.personalInfo?.state ?? ''),
              pincode: String(party.personalInfo?.pincode ?? ''),
              consentAccepted: party.personalInfo?.consentAccepted === true,
              uploadedDocumentTypes: Array.isArray(party.personalInfo?.uploadedDocumentTypes)
                ? (party.personalInfo?.uploadedDocumentTypes as unknown[]).map(String)
                : [],
            })),
        )
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [applicationId, Boolean(coApplicantConfig)])

  function validateCoApplicants(): string | null {
    if (!coApplicantConfig) return null
    if (coApplicants.length < coApplicantMin) return `At least ${coApplicantMin} co-applicant(s) are required.`
    if (coApplicants.length > coApplicantMax) return `At most ${coApplicantMax} co-applicant(s) are allowed.`
    const emails = new Set<string>()
    const mobiles = new Set<string>()
    const primaryEmail = (form.borrowerType === 'INDIVIDUAL' ? form.email : form.contactEmail).trim().toLowerCase()
    const primaryMobile = (form.borrowerType === 'INDIVIDUAL' ? form.mobile || form.borrowerMobile : form.contactMobile).replace(/\D/g, '')
    if (primaryEmail) emails.add(primaryEmail)
    if (primaryMobile.length >= 10) mobiles.add(primaryMobile)
    for (const applicant of coApplicants) {
      const email = applicant.email.trim().toLowerCase()
      const mobile = applicant.mobile.replace(/\D/g, '')
      if (!applicant.fullName.trim() || !email || mobile.length < 10) return 'Complete name, email, and mobile for each co-applicant.'
      if (emails.has(email)) return 'Each applicant must use a different email.'
      if (mobiles.has(mobile)) return 'Each applicant must use a different mobile number.'
      emails.add(email)
      mobiles.add(mobile)
    }
    return coApplicants.length > 0 && !staffMultiPartyPath
      ? 'Choose whether to notify applicants or fill all applicant details yourself.'
      : null
  }

  async function persistCoApplicants(appId: string): Promise<boolean> {
    if (!coApplicantConfig) return true
    try {
      const parties = await upsertApplicationParties(appId, {
        coApplicants: coApplicants.map((applicant) => ({
          id: applicant.id,
          personalInfo: {
            fullName: applicant.fullName.trim(),
            mobile: applicant.mobile.trim(),
            email: applicant.email.trim(),
            ...(applicant.relationship.trim() ? { relationship: applicant.relationship.trim() } : {}),
            ...(applicant.dateOfBirth?.trim() ? { dateOfBirth: applicant.dateOfBirth.trim() } : {}),
            ...(applicant.gender?.trim() ? { gender: applicant.gender.trim() } : {}),
            ...(applicant.occupation?.trim() ? { occupation: applicant.occupation.trim() } : {}),
            ...(applicant.panNumber?.trim() ? { panNumber: applicant.panNumber.trim().toUpperCase() } : {}),
            ...(applicant.aadhaarLast4?.trim() ? { aadhaarLast4: applicant.aadhaarLast4.trim() } : {}),
            ...(applicant.voterId?.trim() ? { voterId: applicant.voterId.trim(), epicNo: applicant.voterId.trim() } : {}),
            ...(applicant.dlNumber?.trim() ? { dlNumber: applicant.dlNumber.trim(), dlNo: applicant.dlNumber.trim() } : {}),
            ...(applicant.bankAccountNumber?.trim()
              ? { bankAccountNumber: applicant.bankAccountNumber.trim(), accountNumber: applicant.bankAccountNumber.trim() }
              : {}),
            ...(applicant.ifscCode?.trim() ? { ifscCode: applicant.ifscCode.trim(), ifsc: applicant.ifscCode.trim() } : {}),
            ...(applicant.bankName?.trim() ? { bankName: applicant.bankName.trim() } : {}),
            ...(applicant.addressLine1?.trim()
              ? { addressLine1: applicant.addressLine1.trim(), currentAddress: applicant.addressLine1.trim() }
              : {}),
            ...(applicant.city?.trim() ? { city: applicant.city.trim() } : {}),
            ...(applicant.state?.trim() ? { state: applicant.state.trim() } : {}),
            ...(applicant.pincode?.trim() ? { pincode: applicant.pincode.trim() } : {}),
            ...(applicant.consentAccepted != null ? { consentAccepted: applicant.consentAccepted } : {}),
            ...(applicant.uploadedDocumentTypes?.length
              ? { uploadedDocumentTypes: applicant.uploadedDocumentTypes }
              : {}),
          },
        })),
      })
      const coApplicantIds = parties.filter((party) => party.role === 'CO_APPLICANT').map((party) => party.id)
      setCoApplicants((current) => current.map((row, index) => ({ ...row, id: coApplicantIds[index] ?? row.id })))
      return true
    } catch (err) {
      setError(intakeErrorMessage(err, 'Could not save co-applicant details.'))
      return false
    }
  }

  const syncDocumentsFromServer = useCallback(async (id: string) => {
    try {
      const docs = await listDocuments(id)
      setForm((f) => {
        const m = { ...f.documentUploaded }
        for (const d of docs) {
          m[d.documentType] = true
        }
        return { ...f, documentUploaded: m }
      })
    } catch {
      // optional — leave local flags
    }
  }, [])

  useEffect(() => {
    if (step === steps.documents && applicationId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- async listDocuments; updates upload flags
      void syncDocumentsFromServer(applicationId)
    }
  }, [step, applicationId, syncDocumentsFromServer, steps.documents])
  useEffect(() => {
    if (step === steps.collateral && needColl && applicationId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void syncDocumentsFromServer(applicationId)
    }
  }, [step, needColl, applicationId, syncDocumentsFromServer, steps.collateral])

  async function persistFromProductStep(): Promise<boolean> {
    if (!applicationId) return true
    setBusy(true)
    try {
      const req = buildIntakeBorrowerUpdate(form, mode, user)
      await updateApplication(applicationId, req)
      return true
    } catch (err) {
      setError(intakeErrorMessage(err, 'Could not save product details.'))
      return false
    } finally {
      setBusy(false)
    }
  }

  async function goNext() {
    setError(null)
    setFieldErrors({})
    setCollateralDocWarn(null)
    if (step === steps.product) {
      const v = validateProductStep(form, mode, activeWorkflows)
      if (v) {
        setError(v)
        return
      }
      if (
        variant === 'staff' &&
        isInvoiceDiscountingProduct(form.loanProduct) &&
        form.invoiceOnboardingChoice === 'ANCHOR'
      ) {
        setAnchorBranch({
          requestedAmount: form.requestedAmount,
          tenureMonths: form.tenureMonths,
        })
        return
      }
      if (needPlpAnchorStep) {
        setBusy(true)
        try {
          if (!applicationId) {
            const req = buildIntakeCreateRequest(form, mode, user)
            const res = await createApplication(req)
            setApplicationId(res.id)
          } else {
            const ok = await persistFromProductStep()
            if (!ok) return
          }
          setStep(steps.plp)
        } catch (err) {
          setError(intakeErrorMessage(err, 'Could not create application.'))
          notifyError(err, 'Could not create application.')
        } finally {
          setBusy(false)
        }
        return
      }
      if (applicationId) {
        const ok = await persistFromProductStep()
        if (!ok) return
        setForm((f) =>
          mode === 'SALES_ASSISTED' && f.borrowerType === 'INDIVIDUAL' && !f.mobile.trim() && f.borrowerMobile
            ? { ...f, mobile: f.borrowerMobile }
            : f,
        )
        setStep(steps.borrower)
        return
      }
      setForm((f) =>
        mode === 'SALES_ASSISTED' && f.borrowerType === 'INDIVIDUAL' && !f.mobile.trim() && f.borrowerMobile
          ? { ...f, mobile: f.borrowerMobile }
          : f,
      )
      setStep(steps.borrower)
      return
    }
    if (needPlpAnchorStep && step === steps.plp) {
      if (!form.selectedSubProgramId) {
        setError('Select an anchor program to continue.')
        return
      }
      if (!applicationId) {
        setError('Application not created yet.')
        return
      }
      setBusy(true)
      try {
        await linkApplicationToProgram(applicationId, form.selectedSubProgramId)
        setStep(steps.borrower)
      } catch (err) {
        setError(intakeErrorMessage(err, 'Could not link program.'))
        notifyError(err, 'Could not link program.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === steps.borrower) {
      // Notify-all path: only name / email / mobile (plus product basics) — applicants complete the rest in portal.
      if (coApplicants.length > 0 && staffMultiPartyPath === 'notify') {
        const basicsErr = validateNotifyBasics(form, mode, activeWorkflows, {
          needPlpProgram: needPlpAnchorStep,
        })
        if (basicsErr) {
          setError(basicsErr)
          return
        }
        const coApplicantError = validateCoApplicants()
        if (coApplicantError) {
          setError(coApplicantError)
          return
        }
        await onNotifyBorrower()
        return
      }
      try {
        await prefetchIntakeGeoForValidation(form)
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Could not load location master data. Try again.')
        return
      }
      const v = validateBorrowerStep(form, mode, selectedWorkflow)
      if (v) {
        setError(v)
        return
      }
      const coApplicantError = validateCoApplicants()
      if (coApplicantError) {
        setError(coApplicantError)
        return
      }
      setBusy(true)
      try {
        const dup = await checkBorrowerIdentity(form, mode, applicationId)
        if (dup) {
          setFieldErrors(dup)
          return
        }
        let appId = applicationId
        if (!appId) {
          const req = buildIntakeCreateRequest(form, mode, user)
          const res = await createApplication(req)
          appId = res.id
          setApplicationId(res.id)
        } else {
          await updateApplication(appId, buildIntakeBorrowerUpdate(form, mode, user))
        }
        if (appId && !(await persistCoApplicants(appId))) {
          return
        }
        setStep(needColl ? steps.collateral : steps.documents)
      } catch (err) {
        const msg = intakeErrorMessage(err, 'Could not save application details.')
        setError(msg)
        notifyError(err, 'Could not save application details.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === steps.collateral && needColl) {
      const v = validateCollateralIntakeStep(form)
      if (v) {
        setError(v)
        return
      }
      setCollateralDocWarn(collateralDocumentMissingWarning(form))
      if (!applicationId) return
      setBusy(true)
      try {
        await persistBorrowerIntakeCollateral(applicationId, form, mode)
        setStep(steps.documents)
      } catch (err) {
        setError(intakeErrorMessage(err, 'Could not save collateral details.'))
        notifyError(err, 'Could not save collateral details.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === steps.documents) {
      const miss = missingIntakeDocumentTypes(form, selectedWorkflow)
      if (miss.length && selectedWorkflow?.intakeConfig?.policy === 'WORKFLOW_DRIVEN') {
        setError(`Required documents missing: ${miss.join(', ')}`)
        return
      }
      if (miss.length) {
        setDocWarning(
          `For a complete package you may still add: ${miss.join(', ')}. You can continue to review, or go back to upload more.`,
        )
      } else {
        setDocWarning(null)
      }
      setStep(steps.consent)
      return
    }
    if (step === steps.consent) {
      const v3 = validateConsentStep(form)
      if (v3) {
        setError(v3)
        return
      }
      if (!applicationId) return
      setBusy(true)
      try {
        await updateApplication(applicationId, buildConsentUpdate(form, mode, user))
        setStep(steps.kyc)
      } catch (err) {
        setError(intakeErrorMessage(err, 'Could not save consents.'))
        notifyError(err, 'Could not save consents.')
      } finally {
        setBusy(false)
      }
      return
    }
    if (step === steps.kyc) {
      const v = validateKycStep(form, selectedWorkflow)
      if (v) {
        setError(v)
        return
      }
      if (!applicationId) return
      setBusy(true)
      try {
        const dup = await checkKycIdentity(form, applicationId)
        if (dup) {
          setFieldErrors(dup)
          return
        }
        await updateApplication(applicationId, buildKycUpdate(form))
        setStep(steps.review)
      } catch (err) {
        const msg = intakeErrorMessage(err, 'Could not save KYC details.')
        setError(msg)
        notifyError(err, 'Could not save KYC details.')
      } finally {
        setBusy(false)
      }
      return
    }
  }

  function goBack() {
    setError(null)
    if (staffCoFillIndex != null) {
      if (staffCoFillIndex > 0) {
        setStaffCoFillIndex(staffCoFillIndex - 1)
      } else {
        setStaffCoFillIndex(null)
        setStep(steps.review)
      }
      return
    }
    if (step > 0) {
      if (applicationId && step === 0) {
        // cannot go back before step0 from elsewhere
        return
      }
      setStep((s) => s - 1)
    }
  }

  async function onNotifyBorrower() {
    if (staffMultiPartyPath === 'staff_fill') {
      setError('Staff-fill applications cannot notify applicants. Continue the wizard to enter each co-applicant’s details.')
      return
    }
    if (anchorBranch) return
    if (!staffCanCreateOrNotify) {
      setError('Only relationship managers and administrators can notify the borrower.')
      return
    }
    const basicsErr = validateNotifyBasics(form, mode, activeWorkflows, {
      needPlpProgram: needPlpAnchorStep,
    })
    if (basicsErr) {
      setError(basicsErr)
      return
    }
    const coApplicantError = validateCoApplicants()
    if (coApplicantError) {
      setError(coApplicantError)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const dup = await checkBorrowerIdentity(form, mode, applicationId)
      if (dup) {
        setFieldErrors(dup)
        return
      }
      for (const applicant of coApplicants) {
        await validateApplicationIdentity({
          applicationId: applicationId ?? undefined,
          asCoApplicant: true,
          email: applicant.email.trim(),
          mobile: applicant.mobile.replace(/\D/g, ''),
          panNumber: applicant.panNumber?.trim() || undefined,
        })
      }
      let appId = applicationId
      if (!appId) {
        const req = buildIntakeCreateRequest(form, mode, user)
        const res = await createApplication(req)
        appId = res.id
        setApplicationId(res.id)
      } else {
        await updateApplication(appId, buildIntakeBorrowerUpdate(form, mode, user))
      }
      if (!(await persistCoApplicants(appId))) return
      // Resume at Documents (first step after basics in the reordered wizard).
      await notifyBorrowerToComplete(appId, steps.documents)
      notifySuccess(
        coApplicants.length > 0
          ? 'All applicants notified to complete the application in the portal.'
          : 'Borrower notified to complete the application in the portal.',
      )
      void navigate(`/applications/${appId}`, { replace: true })
    } catch (err) {
      const msg = intakeErrorMessage(err, 'Could not notify borrower.')
      setError(msg)
      notifyError(err, 'Could not notify borrower.')
    } finally {
      setBusy(false)
    }
  }

  async function onSubmitFinal() {
    if (!applicationId) return
    if (variant === 'staff' && staffMultiPartyPath === 'staff_fill' && coApplicants.length > 0) {
      if (staffCoFillIndex == null) {
        setStaffCoFillIndex(0)
        return
      }
      const applicant = coApplicants[staffCoFillIndex]
      if (!applicant || !applicant.fullName.trim() || !applicant.email.trim() || applicant.mobile.replace(/\D/g, '').length < 10) {
        setError('Complete name, email, and mobile for this co-applicant.')
        return
      }
      if (coApplicantConfig?.personalFields?.dateOfBirth?.required && !applicant.dateOfBirth?.trim()) {
        setError('Date of birth is required for this co-applicant.')
        return
      }
      if (!applicant.consentAccepted) {
        setError('Confirm consent for this co-applicant before continuing.')
        return
      }
      setBusy(true)
      try {
        await validateApplicationIdentity({
          applicationId,
          asCoApplicant: true,
          email: applicant.email.trim(),
          mobile: applicant.mobile.replace(/\D/g, ''),
          panNumber: applicant.panNumber?.trim() || undefined,
        })
        if (!(await persistCoApplicants(applicationId))) return
      } catch (err) {
        setError(intakeErrorMessage(err, 'Could not validate co-applicant identity.'))
        return
      } finally {
        setBusy(false)
      }
      if (staffCoFillIndex < coApplicants.length - 1) {
        setStaffCoFillIndex(staffCoFillIndex + 1)
        return
      }
      setStaffCoFillIndex(null)
    }
    setBusy(true)
    setError(null)
    try {
      // RM editing a case already with them — persist intake changes and return (do not re-run KYC submit).
      if (editApplicationId && isStaffPostSubmitEditStatus(resumedAppStatus)) {
        await updateApplication(applicationId, buildIntakeBorrowerUpdate(form, mode, user))
        if (needColl) {
          await persistBorrowerIntakeCollateral(applicationId, form, mode)
        }
        notifySuccess('Application details updated.')
        void navigate(`/applications/${applicationId}`, { replace: true })
        return
      }
      if (!allConsentsChecked(form)) {
        setError('All consents are required before submission.')
        return
      }
      if (variant === 'staff' && staffMultiPartyPath === 'staff_fill' && coApplicants.length > 0) {
        if (!(await persistCoApplicants(applicationId))) return
        const parties = await listApplicationParties(applicationId)
        for (const party of parties.filter((entry) => entry.role === 'CO_APPLICANT')) {
          await submitApplicationParty(applicationId, party.id)
        }
      }
      let useDelegated = delegatedApp
      if (variant === 'borrower' && !useDelegated) {
        const app = await getApplication(applicationId)
        useDelegated = isDelegatedBorrowerIntake(app)
        if (useDelegated) setDelegatedApp(true)
      }
      if (useDelegated) {
        await submitDelegatedBorrowerIntake(applicationId)
        notifySuccess('Application submitted for lender review.')
        void navigate(`/borrower/applications/${applicationId}`, { replace: true })
      } else {
        await submitApplicationForKyc(applicationId)
        notifySuccess('Application submitted for verification.')
        if (mode === 'BORROWER_SELF_SERVICE') {
          void navigate(`/borrower/applications/${applicationId}`, { replace: true })
        } else {
          void navigate(`/applications/${applicationId}`, { replace: true })
        }
      }
      if (variant === 'borrower') reloadIncomplete()
    } catch (err) {
      const dupField = duplicateFieldFromError(err)
      if (dupField) {
        const dup = duplicateFieldErrors(err)
        if (dup) setFieldErrors(dup)
        const target = intakeStepForDuplicateField(dupField, steps)
        if (target != null) setStep(target)
        notifyError(err, 'Please fix the highlighted identity details before submitting.')
        return
      }
      const msg = intakeErrorMessage(err, 'Could not submit application for verification.')
      setError(msg)
      notifyError(err, 'Could not submit application for verification.')
    } finally {
      setBusy(false)
    }
  }

  async function onUploadFile(documentType: string, file: File | null) {
    if (!file || !applicationId) return
    setError(null)
    setBusy(true)
    try {
      await uploadDocument(applicationId, file, documentType)
      setForm((f) => ({ ...f, documentUploaded: { ...f.documentUploaded, [documentType]: true } }))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed.')
    } finally {
      setBusy(false)
    }
  }

  if (variant === 'borrower' && coApplicantPartyId && resumeApplicationId) {
    return <CoApplicantPortal applicationId={resumeApplicationId} partyId={coApplicantPartyId} />
  }

  if (anchorBranch && variant === 'staff') {
    return (
      <div>
        <PageHeader title={pageTitle(mode)} description={pageDescription(mode)} />
        <p className="mb-4 text-sm text-slate-600">
          <Link to="/applications" className="font-medium text-slate-800 underline">
            ← Applications
          </Link>
        </p>
        <AnchorIntakeWizard
          variant="embedded"
          staffIntakeMode={mode}
          initialRequest={anchorBranch}
          onExitEmbedded={() => {
            setAnchorBranch(null)
            setError(null)
          }}
        />
      </div>
    )
  }

  return (
    <div>
      {variant === 'staff' ? (
        <>
          <PageHeader
            title={editApplicationId ? 'Continue application intake' : pageTitle(mode)}
            description={
              editApplicationId
                ? isStaffPostSubmitEditStatus(resumedAppStatus)
                  ? 'Update borrower and KYC details while this application is with the Relationship Manager. Changes are saved to the existing application.'
                  : 'Resume filling this draft application. Fields follow the active workflow configuration.'
                : pageDescription(mode)
            }
          />
          <p className="mb-4 text-sm text-slate-600">
            <Link
              to={editApplicationId ? `/applications/${editApplicationId}` : '/applications'}
              className="font-medium text-slate-800 underline"
            >
              ← {editApplicationId ? 'Application details' : 'Applications'}
            </Link>
          </p>
        </>
      ) : (
        <div className="mb-6">
          <h1 className="text-2xl font-semibold text-slate-900">{pageTitle(mode)}</h1>
          <p className="mt-1 text-sm text-slate-600">{pageDescription(mode)}</p>
        </div>
      )}

      {hydrating || resumeError ? (
        <div className="mb-4 rounded border border-slate-200 bg-white p-3 text-sm text-slate-800 shadow-sm" role="status">
          {hydrating ? 'Loading your saved application…' : null}
          {resumeError ? <span className="text-rose-800">{resumeError}</span> : null}
        </div>
      ) : null}

      {variant === 'borrower' && sentBackNotes ? (
        <div className="mb-4 rounded border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950">
          <p className="font-medium">Changes requested by the lender</p>
          <p className="mt-1 whitespace-pre-wrap">{sentBackNotes}</p>
        </div>
      ) : null}

      {variant === 'borrower' && incompleteServer.length > 0 && !applicationId ? (
        <div className="mb-4 rounded border border-indigo-200 bg-indigo-50/90 p-4 text-sm text-indigo-950">
          <p className="font-medium">Continue your application</p>
          <p className="mt-1 text-indigo-900">
            You have {incompleteServer.length === 1 ? 'an application' : `${incompleteServer.length} applications`}{' '}
            waiting to be completed. Pick up where you left off.
          </p>
          <ul className="mt-3 space-y-2">
            {incompleteServer.map((a) => (
              <li
                key={a.applicationId}
                className="flex flex-wrap items-center justify-between gap-2 border-b border-indigo-200/80 pb-2 last:border-0 last:pb-0"
              >
                <span>
                  <span className="font-medium">{a.applicationNumber}</span>
                  <span className="text-indigo-800"> — {a.viewerFriendlyStatus ?? a.friendlyStatus}</span>
                </span>
                <button
                  type="button"
                  className="rounded-md bg-indigo-900 px-3 py-1.5 text-xs font-medium text-white"
                  onClick={() => {
                    setSearchParams((prev) => {
                      const n = new URLSearchParams(prev)
                      n.set('resume', a.applicationId)
                      if (a.partyRole === 'CO_APPLICANT' && a.partyId) {
                        n.set('partyId', a.partyId)
                      } else {
                        n.delete('partyId')
                      }
                      return n
                    })
                    setResumeLoaded(false)
                  }}
                >
                  Continue filling
                  {a.partyRole === 'CO_APPLICANT' ? ' (co-applicant)' : ''}
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {variant === 'borrower' && delegatedApp ? (
        <div className="mb-4 rounded border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800">
          Your lender started this application. Complete the remaining steps and submit for their review.
        </div>
      ) : null}

      {workflowsState === 'loading' ? <p className="mb-4 text-sm text-slate-600">Loading active workflows…</p> : null}
      {workflowsState === 'err' && workflowsError ? <ErrorState message={workflowsError} /> : null}
      {workflowsState === 'ok' && activeWorkflows.length === 0 ? (
        <p className="mb-4 bt-alert bt-alert-warning">
          There are no active workflows. Add and activate a workflow in Workflows before creating an application.
        </p>
      ) : null}
      {error ? <ErrorState message={error} /> : null}

      <Stepper step={step} labels={stepLabels} />

      {step === steps.product ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Product &amp; request</h2>
          {variant === 'borrower' &&
          isInvoiceDiscountingProduct(form.loanProduct) &&
          form.selectedSubProgramId ? (
            <LinkedAnchorProgramReadonly subProgramId={form.selectedSubProgramId} />
          ) : null}
          {mode === 'SALES_ASSISTED' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Sales officer name *</span>
                <input
                  className="bt-input w-full"
                  value={form.salesOfficerName}
                  onChange={(e) => setForm((f) => ({ ...f, salesOfficerName: e.target.value }))}
                  disabled={!!applicationId}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Sales / branch ID</span>
                <input
                  className="bt-input w-full"
                  value={form.salesOfficerId}
                  onChange={(e) => setForm((f) => ({ ...f, salesOfficerId: e.target.value }))}
                  disabled={!!applicationId}
                />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Borrower mobile *</span>
                <input
                  type="tel"
                  className="bt-input w-full"
                  value={form.borrowerMobile}
                  onChange={(e) => setForm((f) => ({ ...f, borrowerMobile: e.target.value }))}
                />
              </label>
              <label className="flex items-start gap-2 text-sm text-slate-800 sm:col-span-2">
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={form.salesBorrowerAck}
                  onChange={(e) => setForm((f) => ({ ...f, salesBorrowerAck: e.target.checked }))}
                />
                <span>
                  I confirm the borrower has agreed to start this application and to share the details with the lender. *
                </span>
              </label>
            </div>
          ) : null}
          {variant === 'staff' ? (
            <>
              {mode === 'ADMIN_INTERNAL' ? (
                <p className="text-xs text-slate-500">
                  Internal: pick the loan product first. For invoice discounting, choose borrower or anchor onboarding;
                  all other products use the standard borrower flow unchanged.
                </p>
              ) : null}
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block text-sm text-slate-700 sm:col-span-2">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Loan product *</span>
                  <select
                    className="bt-input w-full text-slate-900 disabled:cursor-not-allowed disabled:bg-slate-50"
                    value={staffProductList.length === 0 ? '' : form.loanProduct}
                    onChange={(e) => {
                      const lp = e.target.value
                      setForm((f) => ({
                        ...f,
                        loanProduct: lp,
                        invoiceOnboardingChoice: isInvoiceDiscountingProduct(lp) ? f.invoiceOnboardingChoice : '',
                      }))
                    }}
                    disabled={workflowsState !== 'ok' || !staffProductList.length || productLocked}
                  >
                    {staffProductList.length === 0 ? <option value="">(none)</option> : null}
                    {staffProductList.map((c) => (
                      <option key={c} value={c}>
                        {workflowLoanProductDisplayName(c)}
                      </option>
                    ))}
                  </select>
                  {selectedWorkflow &&
                  !(isInvoiceDiscountingProduct(form.loanProduct) && form.invoiceOnboardingChoice === 'ANCHOR') ? (
                    <p className="mt-1.5 text-xs text-slate-500">
                      Active workflow: <span className="font-medium text-slate-800">{selectedWorkflow.name}</span> (v
                      {selectedWorkflow.version})
                    </p>
                  ) : null}
                </label>

                {isInvoiceDiscountingProduct(form.loanProduct) ? (
                  <InvoiceOnboardingTypeCards
                    value={form.invoiceOnboardingChoice}
                    onChange={(choice) =>
                      setForm((f) => ({
                        ...f,
                        invoiceOnboardingChoice: choice,
                        ...(choice === 'ANCHOR' ? { purpose: '' } : {}),
                      }))
                    }
                    disabled={productLocked}
                  />
                ) : null}

                {!(isInvoiceDiscountingProduct(form.loanProduct) && form.invoiceOnboardingChoice === 'ANCHOR') ? (
                  <>
                    <label className="block text-sm text-slate-700">
                      <span className="mb-1 block text-xs font-medium text-slate-500">Borrower type *</span>
                      <select
                        className="bt-input w-full text-slate-900"
                        value={form.borrowerType}
                        onChange={(e) => {
                          const bt = e.target.value as BorrowerType
                          const list = productsForBorrowerType(activeWorkflows, bt)
                          setForm((f) => ({
                            ...f,
                            borrowerType: bt,
                            loanProduct: list.some((w) => w.loanProduct === f.loanProduct)
                              ? f.loanProduct
                              : (list[0]?.loanProduct ?? ''),
                          }))
                        }}
                        disabled={workflowsState !== 'ok' || !activeWorkflows.length || productLocked}
                      >
                        {BORROWER_TYPES.map((t) => (
                          <option key={t} value={t}>
                            {BORROWER_TYPE_LABELS[t]}
                          </option>
                        ))}
                      </select>
                    </label>
                    <div className="hidden sm:block" aria-hidden />
                    {variant === 'staff' &&
                    productsForType.length > 0 &&
                    !activeCatalogHasSecuredProduct(activeWorkflows, form.borrowerType) &&
                    (canAccessAdminConfigNav(user?.role ?? '') || import.meta.env.DEV) ? (
                      <p
                        className="sm:col-span-2 mt-2 rounded border border-amber-200 bg-amber-50 px-2 py-1.5 text-xs text-amber-950"
                        role="note"
                      >
                        No secured products are active. Configure workflow for Loan Against Property, Loan Against
                        Securities, or Loan Against Gold.
                      </p>
                    ) : null}
                  </>
                ) : (
                  <p className="sm:col-span-2 text-sm text-slate-600">
                    Next takes you to anchor corporate and identity steps. Amount and tenure below still apply.
                  </p>
                )}

                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Requested amount (INR) *</span>
                  <input
                    type="number"
                    min={0.01}
                    step="0.01"
                    className="bt-input w-full text-slate-900 tabular-nums"
                    value={form.requestedAmount}
                    onChange={(e) => setForm((f) => ({ ...f, requestedAmount: e.target.value }))}
                    required
                  />
                </label>
                <IntakeTenureField
                  workflow={selectedWorkflow}
                  value={form.tenureMonths}
                  lmsTenureUnit={selectedWorkflow?.lmsTenureUnit?.trim() || form.lmsTenureUnit}
                  onChange={(v) => setForm((f) => ({ ...f, tenureMonths: v }))}
                />
                {!isInvoiceDiscountingProduct(form.loanProduct) ? (
                  <LmsWorkflowConfigReadonly
                    lmsProductCode={form.lmsProductCode}
                    lmsTenureUnit={selectedWorkflow?.lmsTenureUnit?.trim() || form.lmsTenureUnit}
                  />
                ) : null}
                {shouldCollectLoanPurposeField(selectedWorkflow, true) &&
                !(
                  isInvoiceDiscountingProduct(form.loanProduct) && form.invoiceOnboardingChoice === 'ANCHOR'
                ) ? (
                  <label className="block text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-1 block text-xs font-medium text-slate-500">
                      Loan purpose
                      {selectedWorkflow?.intakeConfig?.personalFields?.loanPurpose?.required !== false ? ' *' : ''}
                    </span>
                    <select
                      className="bt-input w-full text-slate-900"
                      value={form.loanPurpose}
                      onChange={(e) => {
                        const code = e.target.value
                        setForm((f) => ({
                          ...f,
                          loanPurpose: code,
                          purpose: code ? labelForLoanPurpose(code, selectedWorkflow) : '',
                        }))
                      }}
                    >
                      <option value="">— Select loan purpose —</option>
                      {resolveLoanPurposeOptions(selectedWorkflow).map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  </label>
                ) : null}
              </div>
            </>
          ) : (
            <>
              {mode === 'ADMIN_INTERNAL' ? (
                <p className="text-xs text-slate-500">
                  Internal: application created from this path records your user id in personal info where applicable.
                  Ensure product matches an active workflow for the selected borrower class.
                </p>
              ) : null}
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Borrower type *</span>
                  <select
                    className="bt-input w-full text-slate-900"
                    value={form.borrowerType}
                    onChange={(e) => {
                      const bt = e.target.value as BorrowerType
                      const list = productsForBorrowerType(activeWorkflows, bt)
                      setForm((f) => ({ ...f, borrowerType: bt, loanProduct: list[0]?.loanProduct ?? '' }))
                    }}
                    disabled={workflowsState !== 'ok' || !activeWorkflows.length || productLocked}
                  >
                    {BORROWER_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {BORROWER_TYPE_LABELS[t]}
                      </option>
                    ))}
                  </select>
                </label>
                <div className="text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Loan product *</span>
                  <select
                    className="bt-input w-full text-slate-900 disabled:cursor-not-allowed disabled:bg-slate-50"
                    value={productsForType.length === 0 ? '' : form.loanProduct}
                    onChange={(e) => setForm((f) => ({ ...f, loanProduct: e.target.value }))}
                    disabled={workflowsState !== 'ok' || !productsForType.length || productLocked}
                  >
                    {productsForType.length === 0 ? <option value="">(none for this type)</option> : null}
                    {productsForType.map((w) => (
                      <option key={w.id} value={w.loanProduct}>
                        {workflowLoanProductDisplayName(w.loanProduct)}
                      </option>
                    ))}
                  </select>
                  {selectedWorkflow ? (
                    <p className="mt-1.5 text-xs text-slate-500">
                      Active workflow: <span className="font-medium text-slate-800">{selectedWorkflow.name}</span> (v
                      {selectedWorkflow.version})
                    </p>
                  ) : null}
                </div>
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Requested amount (INR) *</span>
                  <input
                    type="number"
                    min={0.01}
                    step="0.01"
                    className="bt-input w-full text-slate-900 tabular-nums"
                    value={form.requestedAmount}
                    onChange={(e) => setForm((f) => ({ ...f, requestedAmount: e.target.value }))}
                    required
                  />
                </label>
                <IntakeTenureField
                  workflow={selectedWorkflow}
                  value={form.tenureMonths}
                  lmsTenureUnit={selectedWorkflow?.lmsTenureUnit?.trim() || form.lmsTenureUnit}
                  onChange={(v) => setForm((f) => ({ ...f, tenureMonths: v }))}
                />
                {!isInvoiceDiscountingProduct(form.loanProduct) ? (
                  <LmsWorkflowConfigReadonly
                    lmsProductCode={form.lmsProductCode}
                    lmsTenureUnit={selectedWorkflow?.lmsTenureUnit?.trim() || form.lmsTenureUnit}
                  />
                ) : null}
                {shouldCollectLoanPurposeField(selectedWorkflow, true) &&
                !(
                  isInvoiceDiscountingProduct(form.loanProduct) && form.invoiceOnboardingChoice === 'ANCHOR'
                ) ? (
                  <label className="block text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-1 block text-xs font-medium text-slate-500">
                      Loan purpose
                      {selectedWorkflow?.intakeConfig?.personalFields?.loanPurpose?.required !== false ? ' *' : ''}
                    </span>
                    <select
                      className="bt-input w-full text-slate-900"
                      value={form.loanPurpose}
                      onChange={(e) => {
                        const code = e.target.value
                        setForm((f) => ({
                          ...f,
                          loanPurpose: code,
                          purpose: code ? labelForLoanPurpose(code, selectedWorkflow) : '',
                        }))
                      }}
                    >
                      <option value="">— Select loan purpose —</option>
                      {resolveLoanPurposeOptions(selectedWorkflow).map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  </label>
                ) : null}
              </div>
            </>
          )}
          {applicationId && productLocked ? (
            <p className="text-xs text-amber-800">
              Product and borrower class are fixed for this application so the workflow does not get out of sync. You
              can still change amount, tenure, and purpose.
            </p>
          ) : null}
        </section>
      ) : null}

      {needPlpAnchorStep && step === steps.plp ? (
        <SelectAnchorProgramStep
          selectedSubProgramId={form.selectedSubProgramId}
          onSelect={(subProgramId) =>
            setForm((f) => ({ ...f, selectedSubProgramId: subProgramId }))
          }
        />
      ) : null}

      {step === steps.borrower ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Basic borrower details</h2>
          {form.borrowerType === 'INDIVIDUAL' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Full name (as per PAN) *</span>
                <input
                  className="bt-input w-full"
                  value={form.fullName}
                  onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
                  autoComplete="name"
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Mobile *</span>
                <input
                  type="tel"
                  className="bt-input w-full"
                  value={form.mobile}
                  onChange={(e) => {
                    clearFieldError('mobile')
                    setForm((f) => ({ ...f, mobile: e.target.value }))
                  }}
                  autoComplete="tel"
                />
                <IntakeFieldError message={fieldErrors.mobile} />
              </label>
              {mode === 'SALES_ASSISTED' && form.borrowerMobile ? (
                <p className="text-xs text-slate-500 sm:col-span-2">
                  Sales capture: main contact number was <span className="font-mono text-slate-800">{form.borrowerMobile}</span>
                  {form.mobile && form.mobile.replace(/\D/g, '') !== form.borrowerMobile.replace(/\D/g, '') ? (
                    <span> (you are overriding it in the next field for this create)</span>
                  ) : null}
                </p>
              ) : null}
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Email *</span>
                <input
                  type="email"
                  className="bt-input w-full"
                  value={form.email}
                  onChange={(e) => {
                    clearFieldError('email')
                    setForm((f) => ({ ...f, email: e.target.value }))
                  }}
                />
                <IntakeFieldError message={fieldErrors.email} />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">
                  Date of birth
                  {shouldCollectPersonalField(selectedWorkflow, 'dateOfBirth', false) &&
                  selectedWorkflow?.intakeConfig?.personalFields?.dateOfBirth?.required
                    ? ' *'
                    : ''}
                </span>
                <input
                  type="date"
                  className="bt-input w-full"
                  value={form.dateOfBirth}
                  onChange={(e) => setForm((f) => ({ ...f, dateOfBirth: e.target.value }))}
                />
              </label>
              {shouldCollectPersonalField(selectedWorkflow, 'gender', false) ? (
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">
                    Gender
                    {selectedWorkflow?.intakeConfig?.personalFields?.gender?.required ? ' *' : ''}
                  </span>
                  <select
                    className="bt-input w-full"
                    value={form.gender}
                    onChange={(e) => setForm((f) => ({ ...f, gender: e.target.value }))}
                  >
                    <option value="">Select</option>
                    {(selectedWorkflow?.intakeConfig?.personalFields?.gender?.allowedValues ?? [
                      'MALE',
                      'FEMALE',
                      'OTHER',
                      'PREFER_NOT_TO_SAY',
                    ]).map((g) => (
                      <option key={g} value={g}>
                        {g.replaceAll('_', ' ')}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}
              {form.borrowerType === 'INDIVIDUAL' &&
              shouldCollectPersonalField(selectedWorkflow, 'occupation', true) ? (
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">
                    Occupation
                    {selectedWorkflow?.intakeConfig?.personalFields?.occupation?.required !== false ? ' *' : ''}
                  </span>
                  <select
                    className="bt-input w-full"
                    value={form.occupation}
                    onChange={(e) => {
                      const code = e.target.value
                      setForm((f) => ({
                        ...f,
                        occupation: code,
                        occupationIndustry: code ? labelForOccupation(code, selectedWorkflow) : '',
                      }))
                    }}
                  >
                    <option value="">— Select occupation —</option>
                    {resolveOccupationOptions(selectedWorkflow).map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Address</span>
                <input
                  className="bt-input w-full"
                  value={form.addressLine}
                  onChange={(e) => setForm((f) => ({ ...f, addressLine: e.target.value }))}
                />
              </label>
              <IndiaStateCityPincodeFields
                stateValue={form.state}
                cityValue={form.city}
                pincodeValue={form.pincode}
                onStateChange={(v) => setForm((f) => ({ ...f, state: v, city: '' }))}
                onCityChange={(v) => setForm((f) => ({ ...f, city: v }))}
                onPincodeChange={(v) => setForm((f) => ({ ...f, pincode: v }))}
                allowedStateNames={allowedStateNames}
              />
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Business / entity name *</span>
                <input
                  className="bt-input w-full"
                  value={form.businessName}
                  onChange={(e) => setForm((f) => ({ ...f, businessName: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Contact person *</span>
                <input
                  className="bt-input w-full"
                  value={form.contactPersonName}
                  onChange={(e) => setForm((f) => ({ ...f, contactPersonName: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Contact mobile *</span>
                <input
                  type="tel"
                  className="bt-input w-full"
                  value={form.contactMobile}
                  onChange={(e) => {
                    clearFieldError('mobile')
                    setForm((f) => ({ ...f, contactMobile: e.target.value }))
                  }}
                />
                <IntakeFieldError message={fieldErrors.mobile} />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Contact email</span>
                <input
                  type="email"
                  className="bt-input w-full"
                  value={form.contactEmail}
                  onChange={(e) => {
                    clearFieldError('email')
                    setForm((f) => ({ ...f, contactEmail: e.target.value }))
                  }}
                />
                <IntakeFieldError message={fieldErrors.email} />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">GSTIN *</span>
                <input
                  className="bt-input w-full"
                  value={form.gstin}
                  onChange={(e) => setForm((f) => ({ ...f, gstin: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Udyam (if applicable)</span>
                <input
                  className="bt-input w-full"
                  value={form.udyam}
                  onChange={(e) => setForm((f) => ({ ...f, udyam: e.target.value }))}
                />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Business address</span>
                <input
                  className="bt-input w-full"
                  value={form.businessAddress}
                  onChange={(e) => setForm((f) => ({ ...f, businessAddress: e.target.value }))}
                />
              </label>
              <IndiaStateCityPincodeFields
                stateValue={form.businessState}
                cityValue={form.businessCity}
                pincodeValue={form.businessPincode}
                onStateChange={(v) => setForm((f) => ({ ...f, businessState: v, businessCity: '' }))}
                onCityChange={(v) => setForm((f) => ({ ...f, businessCity: v }))}
                onPincodeChange={(v) => setForm((f) => ({ ...f, businessPincode: v }))}
                allowedStateNames={allowedStateNames}
              />
            </div>
          )}
          {isInvoiceDiscountingProduct(form.loanProduct) &&
          (form.invoiceOnboardingChoice === 'BORROWER' || mode === 'BORROWER_SELF_SERVICE') ? (
            <div>
              <p className="mb-2 text-sm font-medium text-slate-800">Anchor relationship details</p>
              <InvoiceDiscountingVintageFields
                form={form}
                onChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
              />
            </div>
          ) : null}
          {needPlpAnchorStep && form.selectedSubProgramId ? (
            <LinkedAnchorProgramReadonly subProgramId={form.selectedSubProgramId} />
          ) : null}
        </section>
      ) : null}

      {step === steps.borrower && coApplicantConfig ? (
        <CoApplicantsSection
          coApplicants={coApplicants}
          onChange={(rows) => {
            setCoApplicants(rows)
            if (rows.length === 0) setStaffMultiPartyPath(null)
          }}
          min={coApplicantMin}
          max={coApplicantMax}
          showCompletionPathChooser
          completionPath={staffMultiPartyPath}
          onCompletionPathChange={setStaffMultiPartyPath}
        />
      ) : null}

      {staffCoFillIndex != null && coApplicants[staffCoFillIndex] ? (
        <StaffCoApplicantDetailForm
          index={staffCoFillIndex}
          total={coApplicants.length}
          row={coApplicants[staffCoFillIndex]!}
          applicationId={applicationId}
          onChange={(patch) =>
            setCoApplicants((rows) =>
              rows.map((row, index) => (index === staffCoFillIndex ? { ...row, ...patch } : row)),
            )
          }
          collectDob={coApplicantConfig?.personalFields?.dateOfBirth?.collect !== false}
          collectGender={coApplicantConfig?.personalFields?.gender?.collect !== false}
          collectOccupation={coApplicantConfig?.personalFields?.occupation?.collect !== false}
          requireDob={coApplicantConfig?.personalFields?.dateOfBirth?.required === true}
          genderOptions={resolveGenderOptions(selectedWorkflow)}
          occupationOptions={resolveOccupationOptions(selectedWorkflow)}
          documentTypes={
            (coApplicantConfig?.standaloneDocuments ?? []).length > 0
              ? (coApplicantConfig?.standaloneDocuments ?? []).map((d) => ({
                  documentType: d.documentType,
                  label: d.label || d.documentType,
                }))
              : undefined
          }
        />
      ) : null}

      {step === steps.collateral && needColl && detectSecuredCollateralKind(form.loanProduct) ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Collateral</h2>
          <p className="text-xs text-slate-600">Secured product — capture the asset offered and upload supporting files.</p>
          <CollateralIntakeFields
            kind={detectSecuredCollateralKind(form.loanProduct)!}
            form={form}
            setForm={setForm}
            applicationId={applicationId}
            onUploadFile={onUploadFile}
            busy={busy}
            documentWarning={collateralDocWarn}
          />
        </section>
      ) : null}

      {step === steps.kyc ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Identity &amp; KYC</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            {shouldShowKycIntakeField(selectedWorkflow, 'PAN_VERIFY', true) ? (
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">PAN *</span>
              <input
                className="bt-input w-full font-mono uppercase"
                value={form.panNumber}
                onChange={(e) => {
                  clearFieldError('panNumber')
                  setForm((f) => ({ ...f, panNumber: e.target.value.toUpperCase() }))
                }}
                maxLength={10}
                autoComplete="off"
              />
              <IntakeFieldError message={fieldErrors.panNumber} />
            </label>
            ) : null}
            {shouldShowKycIntakeField(selectedWorkflow, 'AADHAAR_OTP', true) ? (
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Aadhaar (last 4 digits, or full 12 for internal use)</span>
              <input
                className="bt-input w-full"
                value={form.aadhaar}
                onChange={(e) => setForm((f) => ({ ...f, aadhaar: e.target.value }))}
                inputMode="numeric"
              />
            </label>
            ) : null}
            {shouldShowKycIntakeField(selectedWorkflow, 'VOTER_ID_VERIFY', false) ? (
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Voter ID (EPIC)</span>
              <input
                className="bt-input w-full uppercase"
                value={form.voterId}
                onChange={(e) => setForm((f) => ({ ...f, voterId: e.target.value.toUpperCase() }))}
              />
            </label>
            ) : null}
            {shouldShowKycIntakeField(selectedWorkflow, 'DL_VERIFY', false) ? (
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Driving licence number</span>
              <input
                className="bt-input w-full uppercase"
                value={form.dlNumber}
                onChange={(e) => setForm((f) => ({ ...f, dlNumber: e.target.value.toUpperCase() }))}
              />
            </label>
            ) : null}
            {shouldShowKycIntakeField(selectedWorkflow, 'BANK_PENNY_DROP', false) ? (
              <>
                <label className="block text-sm text-slate-700 sm:col-span-2">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Account number *</span>
                  <input
                    className="bt-input w-full font-mono"
                    value={form.bankAccountNumber}
                    onChange={(e) => {
                      clearFieldError('bankAccountNumber')
                      setForm((f) => ({ ...f, bankAccountNumber: e.target.value }))
                    }}
                    inputMode="numeric"
                  />
                  <IntakeFieldError message={fieldErrors.bankAccountNumber} />
                </label>
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">IFSC *</span>
                  <input
                    className="bt-input w-full font-mono uppercase"
                    value={form.ifscCode}
                    onChange={(e) => {
                      clearFieldError('ifscCode')
                      setForm((f) => ({ ...f, ifscCode: e.target.value.toUpperCase() }))
                    }}
                    maxLength={11}
                  />
                  <IntakeFieldError message={fieldErrors.ifscCode} />
                </label>
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Bank name</span>
                  <input
                    className="bt-input w-full"
                    value={form.bankName}
                    onChange={(e) => setForm((f) => ({ ...f, bankName: e.target.value }))}
                  />
                </label>
              </>
            ) : null}
            {shouldShowKycIntakeField(selectedWorkflow, 'AADHAAR_OTP', true) ? (
            <label className="flex items-center gap-2 text-sm text-slate-800 sm:col-span-2">
              <input
                type="checkbox"
                checked={form.mobileLinkedAadhaar}
                onChange={(e) => setForm((f) => ({ ...f, mobileLinkedAadhaar: e.target.checked }))}
              />
              The mobile number we hold is the same as (or can be used with) the Aadhaar-linked number for verification.
            </label>
            ) : null}
            {isBusinessBorrowerType(form.borrowerType) ? (
              <>
                <p className="text-xs text-slate-500 sm:col-span-2">
                  Business verification: reconfirm GSTIN and Udyam for processing; CIN is required for companies.
                </p>
                {shouldShowKycIntakeField(selectedWorkflow, 'GSTIN_VERIFY', true) ? (
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">GSTIN *</span>
                  <input
                    className="bt-input w-full"
                    value={form.gstin}
                    onChange={(e) => {
                      clearFieldError('gstin')
                      setForm((f) => ({ ...f, gstin: e.target.value }))
                    }}
                  />
                  <IntakeFieldError message={fieldErrors.gstin} />
                </label>
                ) : null}
                {shouldShowKycIntakeField(selectedWorkflow, 'UDYAM_VERIFY', true) ? (
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Udyam</span>
                  <input
                    className="bt-input w-full"
                    value={form.udyam}
                    onChange={(e) => setForm((f) => ({ ...f, udyam: e.target.value }))}
                  />
                </label>
                ) : null}
                {form.borrowerType === 'COMPANY' && shouldShowKycIntakeField(selectedWorkflow, 'CIN_MCA21', true) ? (
                  <label className="block text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-1 block text-xs font-medium text-slate-500">CIN / MCA *</span>
                    <input
                      className="bt-input w-full"
                      value={form.cin}
                      onChange={(e) => setForm((f) => ({ ...f, cin: e.target.value }))}
                    />
                  </label>
                ) : null}
              </>
            ) : null}
          </div>
        </section>
      ) : null}

      {step === steps.consent ? (
        <section className="space-y-3 bt-card p-5">
          <h2 className="bt-card-title">Consents</h2>
          <p className="text-xs text-slate-600">{consentHelper(mode)}</p>
          <div className="space-y-2 text-sm text-slate-800">
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentKyc}
                onChange={(e) => setForm((f) => ({ ...f, consentKyc: e.target.checked }))}
              />
              <span>I consent to KYC verification, including use of the documents and details provided.</span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentBureau}
                onChange={(e) => setForm((f) => ({ ...f, consentBureau: e.target.checked }))}
              />
              <span>I consent to a credit bureau pull and related credit checks for this application.</span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentAccountAggregator}
                onChange={(e) => setForm((f) => ({ ...f, consentAccountAggregator: e.target.checked }))}
              />
              <span>
                I consent to bank statement or account-aggregated data access when required to assess the application.
              </span>
            </label>
            <label className="flex items-start gap-2">
              <input
                type="checkbox"
                className="mt-1"
                checked={form.consentComms}
                onChange={(e) => setForm((f) => ({ ...f, consentComms: e.target.checked }))}
              />
              <span>I consent to receive updates about this application on WhatsApp, SMS, and email.</span>
            </label>
          </div>
        </section>
      ) : null}

      {step === steps.documents && applicationId ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Upload documents</h2>
          <p className="text-sm text-slate-600">Upload a clear copy for each type so underwriters can complete checks without back-and-forth.</p>
          <ul className="space-y-4">
            {documentSlots.map((slot) => (
              <li key={slot.documentType} className="rounded-md border border-slate-100 bg-slate-50/80 p-4">
                <div className="mb-2 text-sm font-medium text-slate-900">{slot.label}</div>
                <p className="mb-2 text-xs text-slate-600">{slot.reason}</p>
                <div className="flex flex-wrap items-center gap-3">
                  <input
                    type="file"
                    accept=".pdf,image/*"
                    className="text-sm"
                    onChange={(e) => {
                      const f = e.target.files?.[0] ?? null
                      if (e.target) e.target.value = ''
                      void onUploadFile(slot.documentType, f)
                    }}
                    disabled={busy}
                  />
                  {form.documentUploaded[slot.documentType] ? (
                    <span className="text-xs font-medium text-emerald-800">Received</span>
                  ) : (
                    <span className="text-xs text-amber-800">Not uploaded</span>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {step === steps.review && applicationId && staffCoFillIndex == null ? (
        <section className="space-y-4 bt-card p-5">
          <h2 className="bt-card-title">Review &amp; submit</h2>
          {docWarning ? (
            <p className="bt-alert bt-alert-warning">{docWarning}</p>
          ) : null}
          <div className="grid gap-3 text-sm sm:grid-cols-2">
            <div className="rounded border border-slate-100 p-3">
              <h3 className="text-xs font-semibold uppercase text-slate-500">Product</h3>
              <p className="mt-1 text-slate-900">{form.loanProduct}</p>
              <p className="text-slate-600">
                {form.requestedAmount ? `INR ${form.requestedAmount}` : '—'}
                {form.tenureMonths
                  ? ` · ${form.tenureMonths} ${tenureMagnitudeShortUnit(form.lmsTenureUnit)}`
                  : ''}
                {!isInvoiceDiscountingProduct(form.loanProduct) && form.lmsTenureUnit
                  ? ` · LMS ${lmsTenureUnitLabel(form.lmsTenureUnit)}`
                  : ''}
                {!isInvoiceDiscountingProduct(form.loanProduct) && form.lmsProductCode
                  ? ` · ${form.lmsProductCode}`
                  : ''}
              </p>
            </div>
            <div className="rounded border border-slate-100 p-3">
              <h3 className="text-xs font-semibold uppercase text-slate-500">Borrower</h3>
              {form.borrowerType === 'INDIVIDUAL' ? (
                <p className="mt-1 text-slate-900">{form.fullName || '—'}</p>
              ) : (
                <p className="mt-1 text-slate-900">{form.businessName || '—'}</p>
              )}
            </div>
            <div className="rounded border border-slate-100 p-3 sm:col-span-2">
              <h3 className="text-xs font-semibold uppercase text-slate-500">KYC &amp; consents</h3>
              <p className="mt-1 text-slate-800">
                PAN {form.panNumber ? '— on file' : 'missing'} · Aadhaar {form.aadhaar ? 'captured' : 'missing'}
              </p>
              <p className="text-slate-800">
                Consents: {allConsentsChecked(form) ? 'all accepted' : 'incomplete (go back)'}
              </p>
            </div>
            {needColl ? (
              <div className="rounded border border-slate-100 p-3 sm:col-span-2">
                <h3 className="text-xs font-semibold uppercase text-slate-500">Collateral (declared)</h3>
                <p className="mt-1 text-sm text-slate-800">
                  {detectSecuredCollateralKind(form.loanProduct) === 'PROPERTY' && `Property — est. ₹${form.collateralEstimatedMarketValue || '—'}`}
                  {detectSecuredCollateralKind(form.loanProduct) === 'SHARES' && `Securities — ISIN ${form.collateralIsin || '—'}`}
                  {detectSecuredCollateralKind(form.loanProduct) === 'GOLD' && `Gold — est. ₹${form.collateralGoldEstimatedValue || '—'}`}
                </p>
              </div>
            ) : null}
            <div className="rounded border border-slate-100 p-3 sm:col-span-2">
              <h3 className="text-xs font-semibold uppercase text-slate-500">Documents</h3>
              <ul className="mt-1 list-inside list-disc text-slate-700">
                {documentSlots.map((s) => (
                  <li key={s.documentType}>
                    {s.label} — {form.documentUploaded[s.documentType] ? 'uploaded' : 'optional / missing (demo)'}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>
      ) : null}

      <div className="mt-6 flex flex-wrap items-center gap-3">
        {step > 0 ? (
          <button
            type="button"
            onClick={goBack}
            disabled={busy}
            className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800"
          >
            Back
          </button>
        ) : null}
        {step < lastStep ? (
          <button
            type="button"
            onClick={() => {
              void goNext()
            }}
            disabled={
              busy ||
              (step === steps.product &&
                (workflowsState !== 'ok' ||
                  (variant === 'staff' &&
                    isInvoiceDiscountingProduct(form.loanProduct) &&
                    !form.invoiceOnboardingChoice))) ||
              (step === steps.kyc && !applicationId) ||
              (step === steps.documents && !applicationId) ||
              (step === steps.consent && !applicationId) ||
              (step === steps.borrower && coApplicants.length > 0 && !staffMultiPartyPath)
            }
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy
              ? 'Please wait…'
              : step === steps.borrower && coApplicants.length > 0 && staffMultiPartyPath === 'notify'
                ? 'Save draft & notify all applicants'
                : 'Continue'}
          </button>
        ) : (
          <button
            type="button"
            onClick={() => {
              void onSubmitFinal()
            }}
            disabled={busy}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy
              ? isStaffPostSubmitEditStatus(resumedAppStatus)
                ? 'Saving…'
                : 'Submitting…'
              : isStaffPostSubmitEditStatus(resumedAppStatus)
                ? 'Save changes'
                : delegatedApp
                  ? 'Submit for review'
                  : variant === 'borrower'
                    ? 'Submit application'
                    : staffMultiPartyPath === 'staff_fill' && coApplicants.length > 0
                      ? staffCoFillIndex == null
                        ? 'Continue to co-applicant details'
                        : staffCoFillIndex === coApplicants.length - 1
                          ? 'Save co-applicants & submit'
                          : 'Save & next co-applicant'
                    : 'Submit for verification'}
          </button>
        )}
        {staffCanCreateOrNotify &&
        step === steps.borrower &&
        !anchorBranch &&
        notifyBasicsOk &&
        coApplicants.length === 0 &&
        !isStaffPostSubmitEditStatus(resumedAppStatus) ? (
          <button
            type="button"
            onClick={() => {
              void onNotifyBorrower()
            }}
            disabled={busy}
            className="rounded-md border border-indigo-400 bg-indigo-50 px-4 py-2 text-sm font-medium text-indigo-950 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy ? 'Please wait…' : 'Save draft & notify borrower'}
          </button>
        ) : null}
        {variant === 'staff' ? (
          <Link
            to={editApplicationId ? `/applications/${editApplicationId}` : '/applications'}
            className="text-sm text-slate-600 underline"
          >
            Cancel
          </Link>
        ) : null}
      </div>
    </div>
  )
}
