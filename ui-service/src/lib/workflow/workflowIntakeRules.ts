import type { IntakeFormState } from '@/lib/intake/intakeTypes'
import {
  DEFAULT_LOAN_PURPOSE_OPTIONS,
  DEFAULT_OCCUPATION_OPTIONS,
  resolveLoanPurposeOptions,
  resolveOccupationOptions,
} from '@/lib/intake/intakeOptionCatalogs'
import type {  WorkflowCoApplicantConfig,
  WorkflowConfigResponse,
  WorkflowIntakeConfig,
  WorkflowIntakeSegment,
  WorkflowMandatoryFieldGroup,
  WorkflowEsignSigningDocument,
  WorkflowEsignSystemDocument,
  WorkflowStandaloneDocument,
  WorkflowTenureRules,
} from '@/types/workflow'
import {
  configuredKycStepNames,
  INTAKE_DOCUMENT_TYPE_LABELS,
  metaForKycStep,
  stepNameFromWorkflowStep,
} from './kycStepIntakeCatalog'
import type { IntakeDocumentSlot } from '@/lib/intake/intakeDocumentSlots'
import { documentSlotsForBorrowerType } from '@/lib/intake/intakeDocumentSlots'
import type { BorrowerType } from '@/types/createApplication'

const PAN_RE = /^[A-Z]{5}[0-9]{4}[A-Z]$/i
const IFSC_RE = /^[A-Z]{4}0[A-Z0-9]{6}$/i

export function defaultWorkflowDrivenIntakeConfig(): WorkflowIntakeConfig {
  return {
    policy: 'WORKFLOW_DRIVEN',
    personalFields: {
      dateOfBirth: { collect: true, required: true },
      gender: {
        collect: true,
        required: false,
        allowedValues: ['MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'],
      },
      occupation: { collect: true, required: true },
      loanPurpose: { collect: true, required: true },
    },
    ageRules: { enabled: false, minAge: 18, maxAge: 70 },
    tenureRules: { inputMode: 'numeric', min: 1, max: 360 },
    locationRules: { allowedStates: [] },
    occupationRules: { options: [...DEFAULT_OCCUPATION_OPTIONS] },
    loanPurposeRules: { options: [...DEFAULT_LOAN_PURPOSE_OPTIONS] },
    mandatoryFieldGroups: [],
    standaloneDocuments: [],
    coApplicant: {
      enabled: false,
      minCoApplicants: 0,
      maxCoApplicants: 3,
      captureAtRmCreate: true,
      notifyAllOnInvite: true,
      primaryKycSteps: [],
      coApplicantKycSteps: ['MOBILE_OTP', 'AADHAAR_OTP', 'PAN_VERIFY'],
      requireAllEsignBeforeDisbursement: true,
      underwritingParty: 'PRIMARY',
      personalFields: {
        dateOfBirth: { collect: true, required: true },
        gender: {
          collect: true,
          required: false,
          allowedValues: ['MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'],
        },
        occupation: { collect: true, required: false },
      },
      ageRules: { enabled: false, minAge: 18, maxAge: 70 },
      occupationRules: { options: [...DEFAULT_OCCUPATION_OPTIONS] },
      mandatoryFieldGroups: [],
      standaloneDocuments: [],
    },
    contacts: { enabled: false, maxUsers: 5 },
  }
}

export function legacyIntakeConfig(): WorkflowIntakeConfig {
  return { policy: 'LEGACY' }
}

/** Normalize workflow intake config from API payloads (camelCase or legacy snake_case). */
export function intakeConfigFromApi(
  workflow: WorkflowConfigResponse | null | undefined,
  fallback?: WorkflowIntakeConfig | null,
): WorkflowIntakeConfig {
  const raw =
    workflow?.intakeConfig ??
    (workflow as { intake_config?: WorkflowIntakeConfig } | null | undefined)?.intake_config
  if (raw && typeof raw === 'object') {
    const policy = String(raw.policy ?? '').toUpperCase()
    if (policy === 'WORKFLOW_DRIVEN') {
      return { ...defaultWorkflowDrivenIntakeConfig(), ...raw, policy: 'WORKFLOW_DRIVEN' }
    }
    return { policy: 'LEGACY', ...raw }
  }
  if (fallback) return fallback
  return legacyIntakeConfig()
}

export function isWorkflowDrivenIntake(workflow: WorkflowConfigResponse | null | undefined): boolean {
  return workflow?.intakeConfig?.policy === 'WORKFLOW_DRIVEN'
}

/** Co-applicant rules for this workflow, or null when not workflow-driven / not enabled. */
export function resolveCoApplicantConfig(
  workflow: WorkflowConfigResponse | null | undefined,
): WorkflowCoApplicantConfig | null {
  if (!isWorkflowDrivenIntake(workflow)) return null
  const cfg = workflow?.intakeConfig?.coApplicant
  return cfg?.enabled ? cfg : null
}

export function isCoApplicantEnabled(workflow: WorkflowConfigResponse | null | undefined): boolean {
  return resolveCoApplicantConfig(workflow) != null
}

/** Anchor contacts/users section — enabled only when workflow configures contacts.enabled. */
export function resolveContactsConfig(
  workflow: WorkflowConfigResponse | null | undefined,
): { enabled: boolean; maxUsers: number } {
  const raw = workflow?.intakeConfig?.contacts
  const enabled = raw?.enabled === true
  const maxUsers = Math.max(1, Number(raw?.maxUsers ?? 5) || 5)
  return { enabled, maxUsers }
}

export function isAnchorContactsEnabled(workflow: WorkflowConfigResponse | null | undefined): boolean {
  return resolveContactsConfig(workflow).enabled
}

export function activeWorkflowForProduct(
  workflows: WorkflowConfigResponse[],
  borrowerType: string,
  loanProduct: string,
): WorkflowConfigResponse | null {
  return (
    workflows.find(
      (w) =>
        w.active &&
        w.borrowerType === borrowerType &&
        w.loanProduct === loanProduct &&
        (w.intakeSegment ?? 'BORROWER') === 'BORROWER',
    ) ?? null
  )
}

function collectAtIntake(step: Record<string, unknown>): boolean {
  if (step.collectAtIntake != null) {
    return step.collectAtIntake === true
  }
  return metaForKycStep(stepNameFromWorkflowStep(step)) != null
}

function fieldRequiredAtIntake(step: Record<string, unknown>): boolean {
  if (step.fieldRequiredAtIntake != null) {
    return step.fieldRequiredAtIntake === true
  }
  return step.mandatory !== false
}

function groupedStepNames(intakeConfig: WorkflowIntakeConfig | null | undefined): Set<string> {
  const out = new Set<string>()
  for (const g of intakeConfig?.mandatoryFieldGroups ?? []) {
    for (const s of g.steps ?? []) {
      out.add(s.toUpperCase())
    }
  }
  return out
}

export function shouldShowKycIntakeField(
  workflow: WorkflowConfigResponse | null | undefined,
  stepName: string,
  legacyFallback: boolean,
): boolean {
  if (!isWorkflowDrivenIntake(workflow)) {
    return legacyFallback
  }
  const names = configuredKycStepNames(workflow?.steps ?? [])
  if (names.length === 0) {
    return legacyFallback
  }
  return names.includes(stepName)
}

export function shouldCollectPersonalField(
  workflow: WorkflowConfigResponse | null | undefined,
  field: 'dateOfBirth' | 'gender' | 'occupation' | 'loanPurpose',
  legacyFallback: boolean,
): boolean {
  if (!isWorkflowDrivenIntake(workflow)) {
    return legacyFallback
  }
  const cfg = workflow?.intakeConfig?.personalFields?.[field]
  return cfg?.collect === true
}

export function shouldCollectLoanPurposeField(
  workflow: WorkflowConfigResponse | null | undefined,
  legacyFallback = true,
): boolean {
  return shouldCollectPersonalField(workflow, 'loanPurpose', legacyFallback)
}

export function resolveTenureRules(workflow: WorkflowConfigResponse | null | undefined): WorkflowTenureRules | null {
  const rules = workflow?.intakeConfig?.tenureRules ?? null
  if (!rules) {
    return null
  }
  if (isWorkflowDrivenIntake(workflow)) {
    return rules
  }
  // Honor explicit dropdown/range config even when policy is still LEGACY / unset.
  if (rules.inputMode === 'dropdown' && (rules.options?.length ?? 0) > 0) {
    return rules
  }
  if (
    rules.inputMode === 'numeric' &&
    ((rules.min != null && rules.min > 0) || (rules.max != null && rules.max > 0))
  ) {
    return rules
  }
  return null
}

/**
 * When workflow-driven and allowedStates is non-empty, return those state names (for dropdown filter).
 * Empty / missing = all master states.
 */
export function resolveAllowedStates(workflow: WorkflowConfigResponse | null | undefined): string[] | null {
  if (!isWorkflowDrivenIntake(workflow)) {
    return null
  }
  const raw = workflow?.intakeConfig?.locationRules?.allowedStates
  if (!Array.isArray(raw) || raw.length === 0) {
    return null
  }
  const names = raw.map((s) => String(s ?? '').trim()).filter(Boolean)
  return names.length > 0 ? names : null
}

function fieldValue(form: IntakeFormState, fieldKey: string): string {
  switch (fieldKey) {
    case 'panNumber':
      return form.panNumber.trim()
    case 'aadhaar':
      return form.aadhaar.replace(/\D/g, '')
    case 'voterId':
      return form.voterId.trim()
    case 'dlNumber':
      return form.dlNumber.trim()
    case 'gstin':
      return form.gstin.trim()
    case 'cin':
      return form.cin.trim()
    case 'udyam':
      return form.udyam.trim()
    case 'bankAccountNumber':
      return form.bankAccountNumber.trim()
    default:
      return ''
  }
}

export function validateWorkflowKycStep(
  form: IntakeFormState,
  workflow: WorkflowConfigResponse | null | undefined,
): string | null {
  if (!isWorkflowDrivenIntake(workflow)) {
    return null
  }
  const steps = workflow?.steps ?? []
  const intakeConfig = workflow?.intakeConfig
  const grouped = groupedStepNames(intakeConfig)

  for (const step of steps) {
    const stepName = stepNameFromWorkflowStep(step)
    if (!stepName || grouped.has(stepName)) {
      continue
    }
    if (!collectAtIntake(step) || !fieldRequiredAtIntake(step)) {
      continue
    }
    const meta = metaForKycStep(stepName)
    if (!meta?.fieldKey) {
      continue
    }
    const value = fieldValue(form, meta.fieldKey)
    if (!value) {
      return `${meta.label} is required for this workflow.`
    }
    if (meta.fieldKey === 'panNumber' && !PAN_RE.test(value)) {
      return 'Enter a valid 10-character PAN (e.g. ABCDE1234F).'
    }
    if (meta.fieldKey === 'aadhaar' && value.length !== 4 && value.length !== 12) {
      return 'Enter the last 4 digits of Aadhaar, or the full 12-digit number.'
    }
    if (stepName === 'BANK_PENNY_DROP') {
      const acct = form.bankAccountNumber.replace(/\D/g, '')
      if (acct.length < 5) {
        return 'Enter a valid bank account number (at least 5 digits).'
      }
      if (!IFSC_RE.test(form.ifscCode.trim())) {
        return 'Enter a valid 11-character IFSC (e.g. HDFC0XXXXXX).'
      }
    }
  }

  const groupError = validateMandatoryGroups(form, workflow)
  if (groupError) {
    return groupError
  }

  const personalError = validateWorkflowPersonalFields(form, workflow)
  if (personalError) {
    return personalError
  }

  const ageError = validateWorkflowAge(form, workflow)
  if (ageError) {
    return ageError
  }

  return null
}

export function validateMandatoryGroups(
  form: IntakeFormState,
  workflow: WorkflowConfigResponse | null | undefined,
): string | null {
  if (!isWorkflowDrivenIntake(workflow)) {
    return null
  }
  const configured = new Set(configuredKycStepNames(workflow?.steps ?? []))
  const groups = workflow?.intakeConfig?.mandatoryFieldGroups ?? []
  for (const group of groups) {
    if (group.logic !== 'ANY') {
      continue
    }
    const members = (group.steps ?? []).map((s) => s.toUpperCase()).filter((s) => configured.has(s))
    if (members.length === 0) {
      continue
    }
    const anyPresent = members.some((member) => {
      const meta = metaForKycStep(member)
      return meta?.fieldKey ? fieldValue(form, meta.fieldKey).length > 0 : false
    })
    if (!anyPresent) {
      return group.label?.trim()
        ? `${group.label} — provide at least one of the configured identity options.`
        : 'At least one government ID is required (Aadhaar, Voter ID, or Driving licence).'
    }
  }
  return null
}

/** Human-readable hint for KYC tab when workflow uses ANY mandatory identity groups. */
export function workflowAnyGroupKycHint(workflow: WorkflowConfigResponse | null | undefined): string | null {
  if (!isWorkflowDrivenIntake(workflow)) {
    return null
  }
  const configured = new Set(configuredKycStepNames(workflow?.steps ?? []))
  const groups = workflow?.intakeConfig?.mandatoryFieldGroups ?? []
  for (const group of groups) {
    if (group.logic !== 'ANY') {
      continue
    }
    const members = (group.steps ?? []).map((s) => s.toUpperCase()).filter((s) => configured.has(s))
    if (members.length < 2) {
      continue
    }
    const labels = members
      .map((m) => metaForKycStep(m)?.label ?? m.replace(/_/g, ' '))
      .join(' OR ')
    if (group.label?.trim()) {
      return `${group.label}: provide and verify at least one of — ${labels}. You do not need to fill every option.`
    }
    return `Provide and verify at least one identity option: ${labels}. You do not need to fill every field shown.`
  }
  return null
}

export function validateWorkflowPersonalFields(
  form: IntakeFormState,
  workflow: WorkflowConfigResponse | null | undefined,
): string | null {
  if (!isWorkflowDrivenIntake(workflow)) {
    return null
  }
  const pf = workflow?.intakeConfig?.personalFields
  if (pf?.dateOfBirth?.collect && pf.dateOfBirth.required && !form.dateOfBirth.trim()) {
    return 'Enter date of birth.'
  }
  if (pf?.gender?.collect && pf.gender.required && !form.gender.trim()) {
    return 'Select gender.'
  }
  if (pf?.gender?.allowedValues?.length && form.gender.trim()) {
    const ok = pf.gender.allowedValues.some((v) => v.toUpperCase() === form.gender.trim().toUpperCase())
    if (!ok) {
      return 'Select a valid gender option.'
    }
  }
  const occupationErr = validateWorkflowOccupation(form, workflow)
  if (occupationErr) return occupationErr
  return null
}

export function validateWorkflowOccupation(
  form: IntakeFormState,
  workflow: WorkflowConfigResponse | null | undefined,
): string | null {
  if (!isWorkflowDrivenIntake(workflow)) {
    return null
  }
  const pf = workflow?.intakeConfig?.personalFields?.occupation
  if (!pf?.collect) {
    return null
  }
  if (pf.required && !form.occupation.trim()) {
    return 'Select occupation.'
  }
  if (form.occupation.trim()) {
    const ok = resolveOccupationOptions(workflow).some((o) => o.value === form.occupation.trim())
    if (!ok) {
      return 'Select a valid occupation option.'
    }
  }
  return null
}

export function validateWorkflowLoanPurpose(
  form: IntakeFormState,
  workflow: WorkflowConfigResponse | null | undefined,
): string | null {
  if (!isWorkflowDrivenIntake(workflow)) {
    return null
  }
  const pf = workflow?.intakeConfig?.personalFields?.loanPurpose
  if (!pf?.collect) {
    return null
  }
  if (pf.required && !form.loanPurpose.trim()) {
    return 'Select a loan purpose.'
  }
  if (form.loanPurpose.trim()) {
    const ok = resolveLoanPurposeOptions(workflow).some((o) => o.value === form.loanPurpose.trim())
    if (!ok) {
      return 'Select a valid loan purpose option.'
    }
  }
  return null
}

export function validateWorkflowAge(
  form: IntakeFormState,
  workflow: WorkflowConfigResponse | null | undefined,
): string | null {
  if (!isWorkflowDrivenIntake(workflow)) {
    return null
  }
  const rules = workflow?.intakeConfig?.ageRules
  if (!rules?.enabled) {
    return null
  }
  if (!form.dateOfBirth.trim()) {
    return 'Enter date of birth for age validation.'
  }
  const dob = new Date(form.dateOfBirth)
  if (Number.isNaN(dob.getTime())) {
    return 'Enter a valid date of birth.'
  }
  const today = new Date()
  let age = today.getFullYear() - dob.getFullYear()
  const m = today.getMonth() - dob.getMonth()
  if (m < 0 || (m === 0 && today.getDate() < dob.getDate())) {
    age -= 1
  }
  if (rules.minAge != null && rules.minAge > 0 && age < rules.minAge) {
    return `Applicant must be at least ${rules.minAge} years old.`
  }
  if (rules.maxAge != null && rules.maxAge > 0 && age > rules.maxAge) {
    return `Applicant must be at most ${rules.maxAge} years old.`
  }
  return null
}

export function validateWorkflowTenure(
  form: IntakeFormState,
  workflow: WorkflowConfigResponse | null | undefined,
): string | null {
  const rules = resolveTenureRules(workflow)
  if (!rules) {
    return null
  }
  const tm = form.tenureMonths.trim()
  if (!tm) {
    return 'Tenure is required for this workflow.'
  }
  const n = Number.parseInt(tm, 10)
  if (Number.isNaN(n) || n <= 0) {
    return 'Enter a valid tenure.'
  }
  if (rules.inputMode === 'dropdown' && rules.options?.length) {
    const ok = rules.options.some((o) => o.value === tm)
    if (!ok) {
      return 'Select a tenure from the allowed options.'
    }
    return null
  }
  if (rules.min != null && rules.min > 0 && n < rules.min) {
    return `Tenure must be at least ${rules.min}.`
  }
  if (rules.max != null && rules.max > 0 && n > rules.max) {
    return `Tenure must be at most ${rules.max}.`
  }
  return null
}

export interface ResolvedIntakeDocumentSlot extends IntakeDocumentSlot {
  required: boolean
}

export function resolveDocumentSlots(
  workflow: WorkflowConfigResponse | null | undefined,
  borrowerType: BorrowerType,
): ResolvedIntakeDocumentSlot[] {
  const byType = new Map<string, ResolvedIntakeDocumentSlot>()
  const labelFor = (documentType: string, fallback?: string) =>
    INTAKE_DOCUMENT_TYPE_LABELS[documentType.toUpperCase()] ??
    fallback ??
    documentType.replaceAll('_', ' ')
  const add = (documentType: string, label: string, required: boolean) => {
    const key = documentType.toUpperCase()
    if (!key) return
    const existing = byType.get(key)
    if (existing) {
      if (required) {
        existing.required = true
      }
      return
    }
    byType.set(key, {
      documentType: key,
      label,
      reason: required ? 'Required for this workflow.' : 'Optional for this workflow.',
      required,
    })
  }

  if (isWorkflowDrivenIntake(workflow)) {
    for (const step of workflow?.steps ?? []) {
      if (!collectAtIntake(step)) {
        continue
      }
      const stepName = stepNameFromWorkflowStep(step)
      const meta = metaForKycStep(stepName)
      const docs = step.documentsRequired as { documentType: string; required?: boolean }[] | undefined
      if (Array.isArray(docs) && docs.length > 0) {
        for (const d of docs) {
          if (d.documentType) {
            add(
              d.documentType,
              labelFor(d.documentType, d.documentType.replaceAll('_', ' ')),
              d.required !== false,
            )
          }
        }
      } else if (step.documentRequired === true && meta) {
        for (const dt of meta.defaultDocumentTypes) {
          add(dt, labelFor(dt, meta.label), true)
        }
      } else if (meta) {
        for (const dt of meta.defaultDocumentTypes) {
          add(dt, labelFor(dt, meta.label), false)
        }
      }
    }
  } else {
    for (const s of documentSlotsForBorrowerType(borrowerType)) {
      add(s.documentType, s.label, false)
    }
  }

  // Standalone docs always apply when configured (even if policy is not WORKFLOW_DRIVEN).
  for (const doc of workflow?.intakeConfig?.standaloneDocuments ?? []) {
    if (!doc.documentType) continue
    add(
      doc.documentType,
      doc.label ?? labelFor(doc.documentType, doc.documentType.replaceAll('_', ' ')),
      doc.required === true,
    )
  }

  // Prefer intakeConfig additionals when the key is set (even empty); legacy step only otherwise.
  const hasIntakeSigningKey =
    workflow?.intakeConfig != null &&
    Object.prototype.hasOwnProperty.call(workflow.intakeConfig, 'esignSigningDocuments')
  if (hasIntakeSigningKey) {
    const intakeSigning = workflow?.intakeConfig?.esignSigningDocuments ?? []
    for (const d of intakeSigning) {
      if (!d?.documentType) continue
      const type = d.documentType.trim().toUpperCase()
      if (!type || type === 'KFS_AGREEMENT' || type === 'ANCHOR_PROGRAM_TERMS' || type === 'SANCTION_LETTER') continue
      const required = d.required !== false
      const collect = d.collectAtIntake === undefined ? required : d.collectAtIntake === true
      if (!collect) continue
      add(
        type,
        d.label ?? labelFor(type, type.replaceAll('_', ' ')),
        required,
      )
    }
  } else {
    // Legacy: eSign additional docs on ESIGN step with collectAtIntake.
    for (const step of workflow?.steps ?? []) {
      const stepName = String((step as { step?: string }).step ?? '').trim().toUpperCase()
      if (stepName !== 'ESIGN_AGREEMENT' && stepName !== 'ESIGN' && stepName !== 'ESIGN_KFS') continue
      const esignDocs = (step as { esignDocuments?: {
        additional?: {
          documentType?: string
          label?: string
          required?: boolean
          collectAtIntake?: boolean
        }[]
      } }).esignDocuments
      if (!esignDocs?.additional?.length) continue
      for (const d of esignDocs.additional) {
        if (!d?.documentType) continue
        const type = d.documentType.trim().toUpperCase()
        if (!type || type === 'KFS_AGREEMENT' || type === 'ANCHOR_PROGRAM_TERMS' || type === 'SANCTION_LETTER') continue
        const required = d.required !== false
        const collect = d.collectAtIntake === undefined ? required : d.collectAtIntake === true
        if (!collect) continue
        add(
          type,
          d.label ?? labelFor(type, type.replaceAll('_', ' ')),
          required,
        )
      }
    }
  }

  const slots = [...byType.values()]
  if (slots.length > 0) return slots
  return documentSlotsForBorrowerType(borrowerType).map((s) => ({ ...s, required: false }))
}

/** Anchor corporate intake excludes individual KYC docs even when workflow steps list them. */
const ANCHOR_EXCLUDED_DOCUMENT_TYPES = new Set(['AADHAAR', 'PHOTOGRAPH'])

export function resolveAnchorDocumentSlots(
  workflow: WorkflowConfigResponse | null | undefined,
  borrowerType: BorrowerType,
): ResolvedIntakeDocumentSlot[] {
  return resolveDocumentSlots(workflow, borrowerType).filter(
    (s) => !ANCHOR_EXCLUDED_DOCUMENT_TYPES.has(s.documentType),
  )
}

export function missingRequiredWorkflowDocuments(
  form: IntakeFormState,
  workflow: WorkflowConfigResponse | null | undefined,
  borrowerType: BorrowerType,
): string[] {
  // Enforce required slots from workflow-driven KYC steps and/or standalone documents.
  return resolveDocumentSlots(workflow, borrowerType)
    .filter((s) => s.required && !form.documentUploaded[s.documentType])
    .map((s) => s.documentType)
}

export function newMandatoryGroup(partial?: Partial<WorkflowMandatoryFieldGroup>): WorkflowMandatoryFieldGroup {
  return {
    id: globalThis.crypto?.randomUUID?.() ?? `g-${Date.now()}`,
    label: '',
    logic: 'ANY',
    steps: [],
    ...partial,
  }
}

export function newStandaloneDocument(partial?: Partial<WorkflowStandaloneDocument>): WorkflowStandaloneDocument {
  return {
    documentType: 'PHOTOGRAPH',
    required: true,
    label: 'Photograph',
    ...partial,
  }
}

export function newEsignSigningDocument(
  partial?: Partial<WorkflowEsignSigningDocument>,
): WorkflowEsignSigningDocument {
  return {
    documentType: 'BOARD_RESOLUTION',
    label: 'Board resolution',
    required: true,
    collectAtIntake: true,
    expectedPageCount: 2,
    ...partial,
  }
}

export function newEsignSystemDocument(
  partial?: Partial<WorkflowEsignSystemDocument>,
): WorkflowEsignSystemDocument {
  return {
    documentKey: 'SANCTION_LETTER',
    label: 'Sanction letter',
    enabled: true,
    expectedPageCount: 2,
    templateFileName: null,
    templateMimeType: null,
    templateBase64: null,
    ...partial,
  }
}

/** Preset system docs suggested in intake UI by segment (does not auto-write config). */
export function suggestedEsignSystemDocuments(
  intakeSegment: WorkflowIntakeSegment | null | undefined,
): WorkflowEsignSystemDocument[] {
  if (intakeSegment === 'ANCHOR') {
    return [
      newEsignSystemDocument({
        documentKey: 'ANCHOR_PROGRAM_TERMS',
        label: 'Anchor program terms',
        enabled: true,
        expectedPageCount: 2,
      }),
      newEsignSystemDocument({
        documentKey: 'SANCTION_LETTER',
        label: 'Sanction letter',
        enabled: false,
        expectedPageCount: 2,
      }),
    ]
  }
  // Borrower segment (incl. invoice discounting borrower): KFS + optional sanction letter
  return [
    newEsignSystemDocument({
      documentKey: 'KFS_AGREEMENT',
      label: 'Key Fact Statement',
      enabled: true,
      expectedPageCount: 2,
    }),
    newEsignSystemDocument({
      documentKey: 'SANCTION_LETTER',
      label: 'Sanction letter',
      enabled: false,
      expectedPageCount: 2,
    }),
  ]
}
