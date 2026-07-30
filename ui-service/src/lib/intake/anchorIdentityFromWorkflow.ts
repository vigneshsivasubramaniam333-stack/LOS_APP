import type { BorrowerType } from '@/types/createApplication'
import type { WorkflowConfigResponse } from '@/types/workflow'
import {
  configuredKycStepNames,
  stepNameFromWorkflowStep,
} from '@/lib/workflow/kycStepIntakeCatalog'

export type AnchorIdentityFieldKey =
  | 'entityPan'
  | 'gstin'
  | 'cin'
  | 'bankAccountNumber'
  | 'ifscCode'
  | 'accountHolderName'

export type AnchorIdentityFieldDef = {
  key: AnchorIdentityFieldKey
  label: string
  required: boolean
  maxLength?: number
}

const IDENTITY_KEYS = new Set<string>([
  'entityPan',
  'gstin',
  'cin',
  'bankAccountNumber',
  'ifscCode',
  'accountHolderName',
])

export function defaultAnchorIdentityFields(borrowerType: BorrowerType): AnchorIdentityFieldDef[] {
  const base: AnchorIdentityFieldDef[] = [
    { key: 'entityPan', label: 'Entity PAN', required: true, maxLength: 10 },
    { key: 'gstin', label: 'GSTIN', required: false, maxLength: 15 },
  ]
  if (borrowerType === 'COMPANY') {
    base.push({ key: 'cin', label: 'CIN (Corporate Identification Number)', required: true, maxLength: 21 })
  } else {
    base.push({ key: 'cin', label: 'CIN (if applicable)', required: false, maxLength: 21 })
  }
  base.push(
    { key: 'bankAccountNumber', label: 'Bank account number', required: true },
    { key: 'ifscCode', label: 'IFSC', required: true, maxLength: 11 },
    { key: 'accountHolderName', label: 'Account holder name', required: true },
  )
  return base
}

function fieldRequiredAtIntake(step: Record<string, unknown>): boolean {
  if (step.fieldRequiredAtIntake != null) {
    return step.fieldRequiredAtIntake === true
  }
  return step.mandatory !== false
}

function collectAtIntake(step: Record<string, unknown>): boolean {
  if (step.collectAtIntake != null) {
    return step.collectAtIntake === true
  }
  // Default: identity KYC steps participate in intake collection.
  return true
}

/**
 * Prefer explicit intakeIdentitySchema; otherwise derive from workflow KYC steps
 * (PAN / GSTIN / CIN / bank). Falls back to legacy defaults when neither is set.
 */
export function resolveAnchorIdentityFields(
  workflow: WorkflowConfigResponse | null | undefined,
  borrowerType: BorrowerType,
): AnchorIdentityFieldDef[] {
  const raw = workflow?.intakeIdentitySchema
  if (Array.isArray(raw) && raw.length > 0) {
    const out: AnchorIdentityFieldDef[] = []
    for (const row of raw) {
      if (!row || typeof row !== 'object') continue
      const m = row as Record<string, unknown>
      const key = String(m.key ?? '')
      if (!IDENTITY_KEYS.has(key)) continue
      out.push({
        key: key as AnchorIdentityFieldKey,
        label: String(m.label ?? key),
        required: Boolean(m.required),
        maxLength: typeof m.maxLength === 'number' ? m.maxLength : undefined,
      })
    }
    if (out.length) return out
  }

  const fromSteps = identityFieldsFromKycSteps(workflow)
  if (fromSteps?.length) return fromSteps

  return defaultAnchorIdentityFields(borrowerType)
}

function identityFieldsFromKycSteps(
  workflow: WorkflowConfigResponse | null | undefined,
): AnchorIdentityFieldDef[] | null {
  const steps = workflow?.steps ?? []
  if (steps.length === 0) return null

  const names = configuredKycStepNames(steps)
  // Only derive when the workflow actually lists identity-related KYC steps.
  const identityRelated = new Set(['PAN_VERIFY', 'GSTIN_VERIFY', 'CIN_MCA21', 'BANK_PENNY_DROP'])
  if (!names.some((n) => identityRelated.has(n))) return null

  const out: AnchorIdentityFieldDef[] = []
  const seen = new Set<string>()

  for (const step of steps) {
    if (!collectAtIntake(step)) continue
    const stepName = stepNameFromWorkflowStep(step)
    if (!stepName || !identityRelated.has(stepName)) continue
    const required = fieldRequiredAtIntake(step)

    if (stepName === 'PAN_VERIFY' && !seen.has('entityPan')) {
      seen.add('entityPan')
      out.push({ key: 'entityPan', label: 'Entity PAN', required, maxLength: 10 })
    }
    if (stepName === 'GSTIN_VERIFY' && !seen.has('gstin')) {
      seen.add('gstin')
      out.push({ key: 'gstin', label: 'GSTIN', required, maxLength: 15 })
    }
    if (stepName === 'CIN_MCA21' && !seen.has('cin')) {
      seen.add('cin')
      out.push({
        key: 'cin',
        label: 'CIN (Corporate Identification Number)',
        required,
        maxLength: 21,
      })
    }
    if (stepName === 'BANK_PENNY_DROP') {
      if (!seen.has('bankAccountNumber')) {
        seen.add('bankAccountNumber')
        out.push({ key: 'bankAccountNumber', label: 'Bank account number', required })
      }
      if (!seen.has('ifscCode')) {
        seen.add('ifscCode')
        out.push({ key: 'ifscCode', label: 'IFSC', required, maxLength: 11 })
      }
      if (!seen.has('accountHolderName')) {
        seen.add('accountHolderName')
        out.push({ key: 'accountHolderName', label: 'Account holder name', required })
      }
    }
  }

  return out.length > 0 ? out : null
}
