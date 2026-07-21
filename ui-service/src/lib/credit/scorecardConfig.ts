/** Parameter value types for condition UI and manual collection. */
export type ScorecardParamType = 'number' | 'yesno' | 'enum' | 'text'

export type ScorecardInputType = 'number' | 'text' | 'dropdown'

export type ScorecardParamDef = {
  value: string
  label: string
  type: ScorecardParamType
  /** Manual credit input key (camelCase) when collected during underwriting */
  manualKey?: string
  enumOptions?: { value: string; label: string }[]
}

export type ScorecardSourceDef = {
  value: string
  label: string
  description?: string
  parameters: ScorecardParamDef[]
  /** Allow free-text parameter names (OTHER source). */
  allowCustomParameter?: boolean
}

export const SCORECARD_SOURCES: ScorecardSourceDef[] = [
  {
    value: 'BUREAU',
    label: 'Bureau',
    description: 'Credit bureau pull or manual bureau override',
    parameters: [{ value: 'BUREAU_SCORE', label: 'Bureau score', type: 'number', manualKey: 'bureauScore' }],
  },
  {
    value: 'KYC',
    label: 'KYC',
    description: 'KYC workflow outcome',
    parameters: [
      { value: 'KYC_PASS', label: 'KYC pass', type: 'yesno', manualKey: 'manualKycOutcome' },
      { value: 'KYC_QUALITY', label: 'KYC quality', type: 'yesno', manualKey: 'manualKycOutcome' },
    ],
  },
  {
    value: 'APPLICATION',
    label: 'Application',
    description: 'Fields from the loan application',
    parameters: [
      { value: 'REQUESTED_AMOUNT', label: 'Requested amount', type: 'number' },
      { value: 'TENURE_MONTHS', label: 'Tenure', type: 'number' },
      { value: 'AGE', label: 'Applicant age (years)', type: 'number' },
      {
        value: 'OCCUPATION',
        label: 'Occupation',
        type: 'enum',
        enumOptions: [
          { value: 'OTHER', label: 'None / Other' },
          { value: 'SELF_EMPLOYED_BUSINESS', label: 'Self Employed / Business' },
          { value: 'SELF_EMPLOYED_PROFESSIONAL', label: 'Self Employed professional' },
          { value: 'SALARIED_PRIVATE', label: 'Salaried — private sector' },
          { value: 'SALARIED_GOVERNMENT', label: 'Salaried — government' },
        ],
      },
      {
        value: 'LOAN_PURPOSE',
        label: 'Loan purpose',
        type: 'enum',
        enumOptions: [
          { value: 'OTHER', label: 'Others' },
          { value: 'SIBLING_MARRIAGE', label: "Sibling's Marriage" },
          { value: 'PURCHASE_DURABLES', label: 'Purchase of Durables' },
          { value: 'BUSINESS_PURPOSE', label: 'Business Purpose' },
          { value: 'OWN_MARRIAGE', label: 'Own Marriage' },
          { value: 'EDUCATION', label: 'Education' },
          { value: 'VEHICLE_PURCHASE', label: 'Vehicle Purchase' },
          { value: 'HOUSE_REPAIR', label: 'House Repair' },
          { value: 'DEBT_CONSOLIDATION', label: 'Debt Consolidation' },
        ],
      },
    ],
  },
  {
    value: 'CONTEXT',
    label: 'Effective context',
    description: 'Resolved income, obligation, and ratios',
    parameters: [
      { value: 'MONTHLY_INCOME', label: 'Monthly income', type: 'number', manualKey: 'monthlyIncome' },
      { value: 'MONTHLY_OBLIGATION', label: 'Monthly obligation', type: 'number', manualKey: 'monthlyObligation' },
      { value: 'EMI_OBLIGATION', label: 'EMI obligation', type: 'number', manualKey: 'emiObligation' },
      { value: 'DTI_RATIO', label: 'DTI ratio (%)', type: 'number', manualKey: 'obligationRatio' },
    ],
  },
  {
    value: 'BANK_STATEMENT',
    label: 'Bank statement',
    description: 'Bank statement analytics and declared income',
    parameters: [
      { value: 'MONTHLY_INCOME', label: 'Monthly income', type: 'number', manualKey: 'monthlyIncome' },
      { value: 'MONTHLY_OBLIGATION', label: 'Monthly obligation', type: 'number', manualKey: 'monthlyObligation' },
      { value: 'DTI_RATIO', label: 'DTI ratio (%)', type: 'number', manualKey: 'obligationRatio' },
      { value: 'avgDailyBalance3m', label: 'Avg daily balance (3m)', type: 'number', manualKey: 'avgDailyBalance3m' },
      { value: 'avgMonthlyTransactions3m', label: 'Avg monthly transactions (3m)', type: 'number', manualKey: 'avgMonthlyTransactions3m' },
      { value: 'avgMonthlySettlements3m', label: 'Avg monthly settlements (3m)', type: 'number', manualKey: 'avgMonthlySettlements3m' },
      { value: 'monthlyTransactions3m', label: 'Monthly transactions (3m)', type: 'number', manualKey: 'monthlyTransactions3m' },
      { value: 'inwardChequeReturns3m', label: 'Inward cheque returns (3m)', type: 'number', manualKey: 'inwardChequeReturns3m' },
      { value: 'avgDailySettlements3m', label: 'Avg daily settlements (3m)', type: 'number', manualKey: 'avgDailySettlements3m' },
      { value: 'noOfTxns60days', label: 'No. of txns (60 days)', type: 'number', manualKey: 'noOfTxns60days' },
      { value: 'txnMth1', label: 'Transactions month 1', type: 'number', manualKey: 'txnMth1' },
      { value: 'txnMth2', label: 'Transactions month 2', type: 'number', manualKey: 'txnMth2' },
      { value: 'txnMth3', label: 'Transactions month 3', type: 'number', manualKey: 'txnMth3' },
    ],
  },
  {
    value: 'GST_STATEMENT',
    label: 'GST statement',
    description: 'GST return / GMV analytics',
    parameters: [
      { value: 'avgGmv3m', label: 'Avg GMV (3m)', type: 'number', manualKey: 'avgGmv3m' },
      { value: 'active90days', label: 'Active 90 days', type: 'number', manualKey: 'active90days' },
      { value: 'GST_INCOME', label: 'GST income', type: 'number', manualKey: 'gstIncome' },
    ],
  },
  {
    value: 'OTHER',
    label: 'Other (manual)',
    description: 'Collected manually during underwriting — personal loan and custom fields',
    allowCustomParameter: true,
    parameters: [
      { value: 'residenceOwned', label: 'Residence owned', type: 'yesno', manualKey: 'residenceOwned' },
      { value: 'residenceStability', label: 'Residence stability (months)', type: 'number', manualKey: 'residenceStability' },
      { value: 'businessStability', label: 'Business stability (years)', type: 'number', manualKey: 'businessStability' },
      { value: 'existingLoanTrackRecordAll', label: 'Existing loan track record (all)', type: 'yesno', manualKey: 'existingLoanTrackRecordAll' },
      { value: 'existingLoanTrackRecord15d', label: 'Existing loan track record (15d)', type: 'yesno', manualKey: 'existingLoanTrackRecord15d' },
      { value: 'qrTxnEDI', label: 'QR txn EDI', type: 'yesno', manualKey: 'qrTxnEDI' },
      { value: 'eligibleOnePointFiveX', label: 'Eligible 1.5×', type: 'yesno', manualKey: 'eligibleOnePointFiveX' },
    ],
  },
  {
    value: 'SCORECARD',
    label: 'Scorecard context',
    description: 'Lookup from effective scorecard map (legacy / computed)',
    allowCustomParameter: true,
    parameters: [
      { value: 'OBLIGATION_RATIO', label: 'Obligation ratio', type: 'number', manualKey: 'obligationRatio' },
      { value: 'LTV', label: 'LTV', type: 'number', manualKey: 'ltv' },
      { value: 'INDUSTRY_RISK', label: 'Industry risk', type: 'enum', manualKey: 'industryRisk', enumOptions: [
        { value: '1', label: 'Low' },
        { value: '0', label: 'Not low' },
      ]},
    ],
  },
  {
    value: 'PROGRAM_INPUTS',
    label: 'Program inputs',
    description: 'Invoice discounting — borrower vintage inputs and program thresholds',
    parameters: [
      { value: 'DEPENDENCY_VINTAGE_PERCENT', label: 'Dependency vintage (%)', type: 'number' },
      { value: 'ANCHOR_RELATIONSHIP_VINTAGE_MONTHS', label: 'Anchor relationship vintage (months)', type: 'number' },
      { value: 'PROGRAM_DEPENDENCY_VINTAGE_PERCENT', label: 'Program min dependency vintage (%)', type: 'number' },
      { value: 'PROGRAM_ANCHOR_RELATIONSHIP_VINTAGE_MONTHS', label: 'Program min anchor vintage (months)', type: 'number' },
    ],
  },
]

export const SCORECARD_SOURCE_OPTIONS = SCORECARD_SOURCES.map((s) => ({ value: s.value, label: s.label }))

export function sourceDef(source: string | undefined | null): ScorecardSourceDef | undefined {
  if (!source) return undefined
  return SCORECARD_SOURCES.find((s) => s.value === source)
}

export function parametersForSource(source: string | undefined | null): ScorecardParamDef[] {
  return sourceDef(source)?.parameters ?? []
}

export function paramDef(source: string, parameter: string): ScorecardParamDef | undefined {
  const params = parametersForSource(source)
  const exact = params.find((p) => p.value === parameter)
  if (exact) return exact
  if (sourceDef(source)?.allowCustomParameter) {
    return { value: parameter, label: parameter.replace(/([A-Z])/g, ' $1').trim(), type: 'number', manualKey: parameter }
  }
  return { value: parameter, label: parameter.replace(/_/g, ' '), type: 'number' }
}

export function defaultParameterForSource(source: string): string {
  const params = parametersForSource(source)
  return params[0]?.value ?? ''
}

export function isKnownParameter(source: string, parameter: string): boolean {
  return parametersForSource(source).some((p) => p.value === parameter)
}

/** PROGRAM_INPUTS is only available for invoice discounting scorecards. */
export function scorecardSourcesForLoanProduct(loanProduct: string): ScorecardSourceDef[] {
  if (loanProduct === 'BUSINESS_WC_INVOICE_DISCOUNTING') {
    return SCORECARD_SOURCES
  }
  return SCORECARD_SOURCES.filter((s) => s.value !== 'PROGRAM_INPUTS')
}

export function scorecardSourceOptionsForLoanProduct(loanProduct: string) {
  return scorecardSourcesForLoanProduct(loanProduct).map((s) => ({ value: s.value, label: s.label }))
}
