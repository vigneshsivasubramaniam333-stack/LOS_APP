/**
 * Aligns with backend `WorkflowConfigResponse` / `WorkflowConfigRequest` JSON.
 */
export type WorkflowIntakeSegment = 'BORROWER' | 'ANCHOR'

export type WorkflowIntakePolicy = 'LEGACY' | 'WORKFLOW_DRIVEN'

export interface WorkflowPersonalFieldConfig {
  collect?: boolean
  required?: boolean
  allowedValues?: string[]
}

export interface WorkflowAgeRules {
  enabled?: boolean
  minAge?: number
  maxAge?: number
}

export interface WorkflowTenureOption {
  value: string
  label: string
  unit?: string
}

export interface WorkflowTenureRules {
  inputMode?: 'numeric' | 'dropdown'
  min?: number
  max?: number
  defaultValue?: string
  options?: WorkflowTenureOption[]
}

/** When allowedStates is non-empty, intake state dropdowns are limited to those names. */
export interface WorkflowLocationRules {
  allowedStates?: string[]
}

/** Allowed intake dropdown value — scores live only on underwriting scorecards. */
export interface WorkflowCodedOption {
  value: string
  label: string
}

export interface WorkflowCodedFieldRules {
  options?: WorkflowCodedOption[]
}

export interface WorkflowMandatoryFieldGroup {
  id: string
  label: string
  logic: 'ANY'
  steps: string[]
}

export interface WorkflowStandaloneDocument {
  documentType: string
  required?: boolean
  label?: string
}

/**
 * Additional multi-doc eSign files (not KFS/program terms / system-generated).
 * Configured under workflow Intake rules — `intakeConfig.esignSigningDocuments`.
 */
export interface WorkflowEsignSigningDocument {
  documentType: string
  label?: string
  /** Required for multi-doc signing before completion; missing at initiate surfaces as error. */
  required?: boolean
  /**
   * When true, required at application create (admin/borrower/anchor intake).
   * When false, may be uploaded later from application review Documents panel.
   */
  collectAtIntake?: boolean
  /** Expected PDF page count on upload validation (default 2). */
  expectedPageCount?: number
}

/**
 * System-generated documents for multi-doc eSign (KFS / program terms / sanction letter).
 * Configured under `intakeConfig.esignSystemDocuments`. Templates are stored for future use;
 * generation currently uses the built-in LOS PDF procedures.
 */
export interface WorkflowEsignSystemDocument {
  documentKey: string
  label?: string
  /** When false, this system document is skipped at eSign initiate. Default true. */
  enabled?: boolean
  expectedPageCount?: number
  /** Original file name of the optional template (not used for generation yet). */
  templateFileName?: string | null
  templateMimeType?: string | null
  /** Base64 template payload stored on workflow config for future generation. */
  templateBase64?: string | null
}

export interface WorkflowStepDocumentRequired {
  documentType: string
  required?: boolean
}

/** Aligns with backend `intakeConfig.coApplicant` (nested JSON) — co-applicant / joint borrower rules. */
export interface WorkflowCoApplicantConfig {
  enabled?: boolean
  maxCoApplicants?: number
  minCoApplicants?: number
  captureAtRmCreate?: boolean
  notifyAllOnInvite?: boolean
  /** Optional override; when empty, primary uses main workflow KYC steps. */
  primaryKycSteps?: string[]
  coApplicantKycSteps?: string[]
  requireAllEsignBeforeDisbursement?: boolean
  underwritingParty?: 'PRIMARY'
  /**
   * Co-applicant-only intake rules (mirrors primary workflow-level params).
   * Primary continues to use top-level intakeConfig fields.
   */
  personalFields?: {
    dateOfBirth?: WorkflowPersonalFieldConfig
    gender?: WorkflowPersonalFieldConfig
    occupation?: WorkflowPersonalFieldConfig
    loanPurpose?: WorkflowPersonalFieldConfig
  }
  ageRules?: WorkflowAgeRules
  occupationRules?: WorkflowCodedFieldRules
  mandatoryFieldGroups?: WorkflowMandatoryFieldGroup[]
  standaloneDocuments?: WorkflowStandaloneDocument[]
}

/** Anchor intake contacts/users capture (enabled on ANCHOR workflows only). */
export interface WorkflowContactsConfig {
  enabled?: boolean
  /** Max users that can be captured during anchor create (including the primary). */
  maxUsers?: number
}

export interface WorkflowIntakeConfig {
  policy?: WorkflowIntakePolicy
  personalFields?: {
    dateOfBirth?: WorkflowPersonalFieldConfig
    gender?: WorkflowPersonalFieldConfig
    occupation?: WorkflowPersonalFieldConfig
    loanPurpose?: WorkflowPersonalFieldConfig
  }
  ageRules?: WorkflowAgeRules
  tenureRules?: WorkflowTenureRules
  locationRules?: WorkflowLocationRules
  occupationRules?: WorkflowCodedFieldRules
  loanPurposeRules?: WorkflowCodedFieldRules
  mandatoryFieldGroups?: WorkflowMandatoryFieldGroup[]
  standaloneDocuments?: WorkflowStandaloneDocument[]
  /**
   * Additional documents for multi-document eSign (system-generated KFS/terms are configured separately).
   */
  esignSigningDocuments?: WorkflowEsignSigningDocument[]
  /**
   * System-generated signing documents (KFS / anchor program terms / optional sanction letter).
   * When omitted, behaviour matches historical default (single default KFS/program-terms only).
   */
  esignSystemDocuments?: WorkflowEsignSystemDocument[]
  coApplicant?: WorkflowCoApplicantConfig
  contacts?: WorkflowContactsConfig
}

export interface WorkflowConfigResponse {
  id: string
  name: string
  borrowerType: string
  loanProduct: string
  /** Encore LMS product code default for this workflow. */
  lmsProductCode?: string | null
  /** Encore tenure unit default (Day, Month, Week). */
  lmsTenureUnit?: string | null
  /** Omitted in older API payloads — treat as BORROWER. */
  intakeSegment?: WorkflowIntakeSegment | null
  intakeIdentitySchema?: Record<string, unknown>[] | null
  intakeConfig?: WorkflowIntakeConfig | null
  steps: Record<string, unknown>[]
  processNotificationMappings?: Record<string, unknown>[] | null
  manualOverridePolicies?: Record<string, unknown>[] | null
  conditionalRules?: Record<string, unknown>[] | null
  vkycTriggerCondition?: Record<string, unknown>[] | null
  workflowPosition?: string | null
  active: boolean
  version: number
  createdAt: string | null
}

export interface WorkflowConfigRequest {
  name: string
  borrowerType: string
  loanProduct: string
  lmsProductCode?: string
  lmsTenureUnit?: string
  intakeSegment?: WorkflowIntakeSegment
  intakeIdentitySchema?: Record<string, unknown>[]
  intakeConfig?: WorkflowIntakeConfig
  steps: Record<string, unknown>[]
  processNotificationMappings?: Record<string, unknown>[]
  manualOverridePolicies?: Record<string, unknown>[]
  conditionalRules?: Record<string, unknown>[]
  vkycTriggerCondition?: Record<string, unknown>[]
  workflowPosition?: string
}
