import type { IntakeFormState } from '@/lib/intake/intakeTypes'
import type {
  WorkflowConfigResponse,
  WorkflowIntakeConfig,
  WorkflowMandatoryFieldGroup,
  WorkflowStandaloneDocument,
  WorkflowTenureRules,
} from '@/types/workflow'
import {
  configuredKycStepNames,
  metaForKycStep,
  stepNameFromWorkflowStep,
} from './kycStepIntakeCatalog'
import type { IntakeDocumentSlot } from '@/lib/intake/intakeDocumentSlots'
import { documentSlotsForBorrowerType } from '@/lib/intake/intakeDocumentSlots'
import type { BorrowerType } from '@/types/createApplication'

const PAN_RE = /^[A-Z]{5}[0-9]{4}[A-Z]$/i

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
    },
    ageRules: { enabled: false, minAge: 18, maxAge: 70 },
    tenureRules: { inputMode: 'numeric', min: 1, max: 360 },
    mandatoryFieldGroups: [],
    standaloneDocuments: [],
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
  field: 'dateOfBirth' | 'gender',
  legacyFallback: boolean,
): boolean {
  if (!isWorkflowDrivenIntake(workflow)) {
    return legacyFallback
  }
  const cfg = workflow?.intakeConfig?.personalFields?.[field]
  return cfg?.collect === true
}

export function resolveTenureRules(workflow: WorkflowConfigResponse | null | undefined): WorkflowTenureRules | null {
  if (!isWorkflowDrivenIntake(workflow)) {
    return null
  }
  return workflow?.intakeConfig?.tenureRules ?? null
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
  if (!isWorkflowDrivenIntake(workflow)) {
    return documentSlotsForBorrowerType(borrowerType).map((s) => ({ ...s, required: false }))
  }

  const byType = new Map<string, ResolvedIntakeDocumentSlot>()
  const add = (documentType: string, label: string, required: boolean) => {
    const key = documentType.toUpperCase()
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
          add(d.documentType, d.documentType.replaceAll('_', ' '), d.required !== false)
        }
      }
    } else if (step.documentRequired === true && meta) {
      for (const dt of meta.defaultDocumentTypes) {
        add(dt, dt.replaceAll('_', ' '), true)
      }
    } else if (meta) {
      for (const dt of meta.defaultDocumentTypes) {
        add(dt, dt.replaceAll('_', ' '), false)
      }
    }
  }

  for (const doc of workflow?.intakeConfig?.standaloneDocuments ?? []) {
    add(doc.documentType, doc.label ?? doc.documentType.replaceAll('_', ' '), doc.required === true)
  }

  return [...byType.values()]
}

export function missingRequiredWorkflowDocuments(
  form: IntakeFormState,
  workflow: WorkflowConfigResponse | null | undefined,
  borrowerType: BorrowerType,
): string[] {
  if (!isWorkflowDrivenIntake(workflow)) {
    return []
  }
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
