export type PlpSyncStatus = 'NOT_SYNCED' | 'SYNC_SUCCESS' | 'SYNC_FAILED'

export type ProgramApprovalStatus =
  | 'DRAFT'
  | 'PENDING_L2'
  | 'SENT_BACK'
  | 'APPROVED'
  | 'REJECTED'

export interface PlpProgramSummary {
  programId: string
  subProgramId: string | null
  programName: string
  programCode: string
  programType: string
  creditLimit: number | null
  interestRate: number | null
  tenureDays: number | null
  currency: string | null
  validityStartDate: string | null
  validityEndDate: string | null
  anchorId: string | null
  anchorName: string | null
  anchorCode: string | null
  programSyncStatus: PlpSyncStatus
  subProgramSyncStatus: PlpSyncStatus | null
  borrowerCount: number
  flowType?: string | null
  lmsEntryIn?: string | null
  encoreProductCode?: string | null
  approvalStatus?: ProgramApprovalStatus | null
  approvalNotes?: string | null
  dependencyVintagePercent?: number | null
  anchorRelationshipVintageMonths?: number | null
  interestPayment?: string | null
  maxInvoiceVintageDays?: number | null
  maxCmr?: number | null
  minCibil?: number | null
  customFields?: Record<string, unknown> | null
}

export interface PlpProgramSetupResponse {
  programId: string
  subProgramId: string
  anchorId: string
  programName: string
  programType: string
  creditLimit: number | null
  interestRate: number | null
  tenureDays: number | null
  currency: string | null
  validityStartDate: string | null
  validityEndDate: string | null
  programSyncStatus: PlpSyncStatus
  programSyncError?: string | null
  subProgramSyncStatus: PlpSyncStatus
  subProgramSyncError?: string | null
  plpProgramId: string | null
  plpSubProgramId: string | null
  programSyncedAt: string | null
  subProgramSyncedAt: string | null
  approvalStatus?: ProgramApprovalStatus | null
  approvalNotes?: string | null
  assignedL1UserId?: string | null
  assignedL2UserId?: string | null
  dependencyVintagePercent?: number | null
  anchorRelationshipVintageMonths?: number | null
  interestPayment?: string | null
  maxInvoiceVintageDays?: number | null
  maxCmr?: number | null
  minCibil?: number | null
  customFields?: Record<string, unknown> | null
}

export interface CreatePlpProgramRequest {
  anchorId: string
  programName: string
  programType: string
  creditLimit?: number
  interestRate?: number
  tenureDays?: number
  currency?: string
  validityStartDate?: string
  validityEndDate?: string
  subProgramCode?: string
  subProgramName?: string
  subProgramLimit?: number
  flowType?: string
  lmsEntryIn?: string
  encoreProductCode?: string
  dependencyVintagePercent?: number
  anchorRelationshipVintageMonths?: number
  interestPayment?: string
  maxInvoiceVintageDays?: number
  maxCmr?: number
  minCibil?: number
  customFields?: Record<string, string | number>
}

export interface ProgramFieldDefinition {
  id: string
  fieldKey: string
  label: string
  inputType: 'TEXT' | 'NUMBER' | 'DROPDOWN' | string
  options?: { value?: string; label?: string }[] | null
  required: boolean
  active: boolean
  sortOrder: number
  productTypes?: string[] | null
  systemManaged: boolean
  helpText?: string | null
  storageTarget?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface ProgramFieldDefinitionRequest {
  fieldKey?: string
  label: string
  inputType: string
  options?: { value: string; label: string }[]
  required?: boolean
  active?: boolean
  sortOrder?: number
  productTypes?: string[]
  helpText?: string
}

export interface PlpLinkedSubProgramSummary {
  subProgramId: string
  programId: string
  anchorId: string
  subProgramName: string
  programName: string
  programType: string
  anchorName: string
  anchorCode: string
  programLimit: number | null
  programSyncStatus: PlpSyncStatus
  subProgramSyncStatus: PlpSyncStatus
  plpProgramId: string | null
  plpSubProgramId: string | null
  dependencyVintagePercent?: number | null
  anchorRelationshipVintageMonths?: number | null
}

export interface VintageEligibility {
  borrowerDependencyVintagePercent: number | null
  borrowerAnchorRelationshipVintageMonths: number | null
  programDependencyVintagePercent: number | null
  programAnchorRelationshipVintageMonths: number | null
  dependencyVintageStatus: 'ELIGIBLE' | 'LOWER' | 'NOT_CONFIGURED' | string
  dependencyVintageMessage: string | null
  anchorVintageStatus: 'ELIGIBLE' | 'LOWER' | 'NOT_CONFIGURED' | string
  anchorVintageMessage: string | null
}

export interface PlpProgramDetail {
  programId: string
  programCode: string
  programName: string
  programType: string
  creditLimit: number | null
  maxBorrowerLimit: number | null
  interestRate: number | null
  tenureDays: number | null
  currency: string | null
  validityStartDate: string | null
  validityEndDate: string | null
  programSyncStatus: PlpSyncStatus
  programSyncError: string | null
  programSyncedAt: string | null
  plpProgramId: string | null
  subPrograms: PlpSubProgramView[]
  borrowers: PlpBorrowerApplicationView[]
}

export interface PlpSubProgramView {
  subProgramId: string
  subProgramCode: string
  name: string
  anchorId: string
  anchorName: string | null
  anchorSyncStatus: PlpSyncStatus | null
  subProgramSyncStatus: PlpSyncStatus
  subProgramSyncError: string | null
  subProgramSyncedAt: string | null
  plpSubProgramId: string | null
  borrowerCount: number
}

export interface PlpBorrowerApplicationView {
  applicationId: string
  applicationNumber: string
  borrowerName: string
  status: string
  subProgramId: string
  borrowerSyncStatus: PlpSyncStatus
  linkSyncStatus: PlpSyncStatus
  mappingSyncStatus: PlpSyncStatus
  overallSyncStatus: PlpSyncStatus
}

export interface AnchorMasterView {
  id: string
  code: string
  name: string
  plpAnchorSyncStatus: PlpSyncStatus
  plpAnchorId: string | null
  sourceAnchorApplicationId: string | null
}

export interface PlpSyncResult {
  id: string
  syncStatus: PlpSyncStatus
  syncError: string | null
  syncedAt: string | null
  plpId: string | null
}
