import { helpForParameter } from '@/lib/scorecardParameterSources'

export type ScorecardParameterRow = {
  parameter?: string
  rowId?: string
  source?: string
  condition?: string
  weight?: number
  matched?: boolean
  pointsEarned?: number
  maxScore?: number
  valueUsed?: string
  valueSource?: string
  attachment?: string
}

export type ScorecardCategoryGroup = {
  category: string
  categoryScore: number
  rows: Array<{
    criterion: string
    description: string
    weightPercent: number
    score: number
    row: ScorecardParameterRow
  }>
}

const CATEGORY_BY_PARAMETER: Record<string, string> = {
  BUREAU_SCORE: 'Credit History',
  REPAYMENT_HISTORY: 'Credit History',
  MONTHLY_INCOME: 'Financial Strength',
  GST_INCOME: 'Financial Strength',
  BANK_STATEMENT_INCOME: 'Financial Strength',
  AVERAGE_BANK_BALANCE: 'Financial Strength',
  OBLIGATION_RATIO: 'Financial Strength',
  EMI_OBLIGATION: 'Financial Strength',
  EBITDA_PROXY: 'Financial Strength',
  LEVERAGE_RATIO: 'Financial Strength',
  KYC_QUALITY: 'Borrower Profile',
  KYC_PASS: 'Borrower Profile',
  BUSINESS_VINTAGE_MONTHS: 'Borrower Profile',
  INDUSTRY_RISK: 'Borrower Profile',
  PROPERTY_VALUE: 'Transaction Risk',
  LTV: 'Transaction Risk',
  REQUESTED_AMOUNT: 'Transaction Risk',
  TENURE_MONTHS: 'Transaction Risk',
}

const CATEGORY_ORDER = [
  'Credit History',
  'Financial Strength',
  'Borrower Profile',
  'Transaction Risk',
  'Other',
] as const

function categoryForParameter(parameter: string | undefined): string {
  if (!parameter) return 'Other'
  return CATEGORY_BY_PARAMETER[parameter.toUpperCase()] ?? 'Other'
}

function rowDisplayScore(row: ScorecardParameterRow): number {
  const earned = row.pointsEarned ?? 0
  const max = row.maxScore ?? 0
  if (max > 0) {
    return Math.round((earned / max) * 1000) / 10
  }
  return earned
}

function criterionLabel(row: ScorecardParameterRow): string {
  const param = row.parameter ?? '—'
  const help = helpForParameter(param)
  if (row.condition) {
    return `${help.label} (${row.condition})`
  }
  return help.label
}

function criterionDescription(row: ScorecardParameterRow): string {
  const help = helpForParameter(row.parameter)
  if (help.sources.length > 0) {
    return help.sources[0]
  }
  return row.source ? `Sourced via ${row.source.replace(/_/g, ' ')}` : '—'
}

/** Groups scorecard parameter rows into LOS-style categories for underwriting summary display. */
export function buildScorecardCategoryGroups(
  parameterResults: ScorecardParameterRow[] | undefined | null,
): ScorecardCategoryGroup[] {
  if (!parameterResults?.length) return []

  const buckets = new Map<string, ScorecardCategoryGroup['rows']>()
  for (const row of parameterResults) {
    const category = categoryForParameter(row.parameter)
    const list = buckets.get(category) ?? []
    list.push({
      criterion: criterionLabel(row),
      description: criterionDescription(row),
      weightPercent: row.weight ?? 0,
      score: rowDisplayScore(row),
      row,
    })
    buckets.set(category, list)
  }

  return CATEGORY_ORDER.filter((c) => buckets.has(c)).map((category) => {
    const rows = buckets.get(category) ?? []
    const categoryScore = rows.reduce((sum, r) => sum + (r.row.pointsEarned ?? 0), 0)
    return { category, categoryScore, rows }
  })
}

export function aggregateScorecardPoints(parameterResults: ScorecardParameterRow[] | undefined | null): number {
  if (!parameterResults?.length) return 0
  return parameterResults.reduce((sum, r) => sum + (r.pointsEarned ?? 0), 0)
}
