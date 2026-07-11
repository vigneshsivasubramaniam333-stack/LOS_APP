/**
 * Human-readable map: scorecard parameter → how LOS can source it.
 * Shown in Application → Underwriting for credit manager orientation (not a second policy engine).
 */
export const SCORECARD_PARAMETER_SOURCE_HELP: Record<
  string,
  { label: string; sources: string[] }
> = {
  BUREAU_SCORE: {
    label: 'Bureau score',
    sources: ['Equifax (or configured bureau) pull', 'Manual bureau override', 'Bureau report document'],
  },
  MONTHLY_INCOME: {
    label: 'Monthly income',
    sources: [
      'Application / borrower financials',
      'Manual credit input (income = MANUAL)',
      'Bank statement (declared in manual inputs)',
    ],
  },
  GST_INCOME: {
    label: 'GST / assessed business income',
    sources: ['GST integration (when wired)', 'GST return document + manual', 'Manual GST income field'],
  },
  BANK_STATEMENT_INCOME: {
    label: 'Bank statement income',
    sources: ['Account aggregator (when wired)', 'Bank statement document', 'Manual bankStatementIncome'],
  },
  AVERAGE_BANK_BALANCE: {
    label: 'Average bank balance',
    sources: ['Bank statement / AA', 'Manual averageBankBalance'],
  },
  OBLIGATION_RATIO: {
    label: 'Obligation / FOIR ratio',
    sources: [
      'System: monthly obligation ÷ income (from effective credit control)',
      'Manual obligationRatio (overrides ratio when set)',
    ],
  },
  EMI_OBLIGATION: {
    label: 'Monthly EMI / obligation',
    sources: ['Application financials', 'Manual emiObligation / monthlyObligation'],
  },
  PROPERTY_VALUE: {
    label: 'Property / collateral value',
    sources: ['Valuation report document', 'Manual propertyValue (collateral section when available)'],
  },
  LTV: {
    label: 'Loan-to-value',
    sources: [
      'System: requested amount ÷ property value (when both present)',
      'Manual ltv',
    ],
  },
  KYC_QUALITY: {
    label: 'KYC quality / outcome',
    sources: [
      'KYC workflow outcome (provider)',
      'Manual KYC outcome when KYC source = MANUAL',
    ],
  },
  KYC_PASS: {
    label: 'KYC pass (legacy row)',
    sources: ['Provider KYC outcome', 'Manual KYC when selected'],
  },
  BUSINESS_VINTAGE_MONTHS: {
    label: 'Business vintage (months)',
    sources: ['Business proof / GST', 'Manual businessVintageMonths'],
  },
  DEPENDENCY_VINTAGE_PERCENT: {
    label: 'Dependency vintage (%)',
    sources: ['Invoice discounting borrower intake', 'Program inputs scorecard source'],
  },
  ANCHOR_RELATIONSHIP_VINTAGE_MONTHS: {
    label: 'Anchor relationship vintage (months)',
    sources: ['Invoice discounting borrower intake', 'Program inputs scorecard source'],
  },
  PROGRAM_DEPENDENCY_VINTAGE_PERCENT: {
    label: 'Program min dependency vintage (%)',
    sources: ['PLP / LOS program configuration'],
  },
  PROGRAM_ANCHOR_RELATIONSHIP_VINTAGE_MONTHS: {
    label: 'Program min anchor vintage (months)',
    sources: ['PLP / LOS program configuration'],
  },
  INDUSTRY_RISK: {
    label: 'Industry risk band',
    sources: ['Credit note / policy', 'Manual industryRisk (LOW / MED / HIGH)'],
  },
  LEVERAGE_RATIO: {
    label: 'Leverage ratio',
    sources: ['Financial statements', 'Manual leverageRatio'],
  },
  EBITDA_PROXY: {
    label: 'EBITDA (proxy)',
    sources: ['Financials upload', 'Manual ebitdaProxy'],
  },
  REPAYMENT_HISTORY: {
    label: 'Repayment / conduct',
    sources: ['Bank statement / bureau', 'Manual repaymentHistory (CLEAN / …)'],
  },
  REQUESTED_AMOUNT: { label: 'Requested loan amount', sources: ['Application'] },
  TENURE_MONTHS: { label: 'Tenure', sources: ['Application'] },
  avgDailyBalance3m: {
    label: 'Avg daily balance (3m)',
    sources: ['Bank statement analytics', 'Manual credit → Scorecard metrics'],
  },
  avgGmv3m: {
    label: 'Avg GMV (3m)',
    sources: ['GST statement', 'Manual credit → Scorecard metrics'],
  },
  residenceOwned: {
    label: 'Residence owned',
    sources: ['Manual credit → Scorecard metrics (OTHER)'],
  },
  eligibleOnePointFiveX: {
    label: 'Eligible 1.5×',
    sources: ['Manual credit → Scorecard metrics (OTHER)'],
  },
}

export function helpForParameter(parameter: string | undefined | null) {
  if (!parameter) {
    return { label: '—', sources: [] as string[] }
  }
  return (
    SCORECARD_PARAMETER_SOURCE_HELP[parameter] ?? {
      label: parameter.replace(/_/g, ' '),
      sources: ['See effective scorecard context (credit control) or add manual under Manual credit inputs'],
    }
  )
}
