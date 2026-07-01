export interface KycStepIntakeMeta {
  fieldKey: string | null
  defaultDocumentTypes: string[]
  label: string
}

export const KYC_STEP_INTAKE_CATALOG: Record<string, KycStepIntakeMeta> = {
  PAN_VERIFY: { fieldKey: 'panNumber', defaultDocumentTypes: ['PAN_CARD'], label: 'PAN' },
  AADHAAR_OTP: { fieldKey: 'aadhaar', defaultDocumentTypes: ['AADHAAR'], label: 'Aadhaar' },
  VOTER_ID_VERIFY: { fieldKey: 'voterId', defaultDocumentTypes: [], label: 'Voter ID' },
  DL_VERIFY: { fieldKey: 'dlNumber', defaultDocumentTypes: [], label: 'Driving licence' },
  GSTIN_VERIFY: { fieldKey: 'gstin', defaultDocumentTypes: ['GST_RETURN'], label: 'GSTIN' },
  BANK_PENNY_DROP: { fieldKey: 'bankAccountNumber', defaultDocumentTypes: ['BANK_STATEMENT'], label: 'Bank account' },
  FACE_MATCH: { fieldKey: null, defaultDocumentTypes: ['PHOTOGRAPH'], label: 'Face match' },
  LIVENESS: { fieldKey: null, defaultDocumentTypes: ['PHOTOGRAPH'], label: 'Liveness' },
  UDYAM_VERIFY: { fieldKey: 'udyam', defaultDocumentTypes: [], label: 'Udyam' },
  CIN_MCA21: { fieldKey: 'cin', defaultDocumentTypes: [], label: 'CIN' },
}

export function metaForKycStep(step: string): KycStepIntakeMeta | null {
  return KYC_STEP_INTAKE_CATALOG[step.trim().toUpperCase()] ?? null
}

export function stepNameFromWorkflowStep(step: Record<string, unknown>): string {
  return String(step.step ?? '').trim().toUpperCase()
}

export function configuredKycStepNames(steps: Record<string, unknown>[]): string[] {
  return steps.map((s) => stepNameFromWorkflowStep(s)).filter(Boolean)
}
