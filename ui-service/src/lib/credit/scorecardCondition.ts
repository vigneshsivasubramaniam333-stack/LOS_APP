import type { ScorecardParamDef, ScorecardParamType } from './scorecardConfig'

export type ConditionOp =
  | 'GTE'
  | 'GT'
  | 'LTE'
  | 'LT'
  | 'EQ'
  | 'NE'
  | 'CONTAINS'
  | 'NOT_CONTAINS'
  | 'BETWEEN'

export type ParsedCondition =
  | { op: 'BETWEEN'; min: string; max: string }
  | { op: Exclude<ConditionOp, 'BETWEEN'>; value: string }

const OP_LABELS: Record<ConditionOp, string> = {
  GTE: 'At least (≥)',
  GT: 'Greater than (>)',
  LTE: 'At most (≤)',
  LT: 'Less than (<)',
  EQ: 'Equals',
  NE: 'Not equal',
  CONTAINS: 'Contains',
  NOT_CONTAINS: 'Does not contain',
  BETWEEN: 'Between (inclusive)',
}

const TEXT_OP_LABELS: Partial<Record<ConditionOp, string>> = {
  EQ: 'Matches',
  NE: 'Does not match',
  CONTAINS: 'Contains',
  NOT_CONTAINS: 'Does not contain',
}

export function conditionOpLabel(op: ConditionOp, type?: ScorecardParamType): string {
  if (type === 'text' && TEXT_OP_LABELS[op]) return TEXT_OP_LABELS[op]!
  return OP_LABELS[op]
}

export function opsForParamType(type: ScorecardParamType): ConditionOp[] {
  if (type === 'text') {
    return ['EQ', 'NE', 'CONTAINS', 'NOT_CONTAINS']
  }
  if (type === 'yesno' || type === 'enum') {
    return ['EQ', 'NE']
  }
  return ['GTE', 'GT', 'LTE', 'LT', 'EQ', 'NE', 'BETWEEN']
}

export function parseCondition(raw: string | undefined | null): ParsedCondition | null {
  if (!raw?.trim()) return null
  const c = raw.trim()
  const first = c.indexOf(':')
  if (first < 0) return null
  const op = c.substring(0, first).trim().toUpperCase() as ConditionOp
  const rest = c.substring(first + 1).trim()
  if (op === 'BETWEEN') {
    const mid = rest.indexOf(':')
    if (mid < 0) return null
    return { op: 'BETWEEN', min: rest.substring(0, mid).trim(), max: rest.substring(mid + 1).trim() }
  }
  if (['GTE', 'GT', 'LTE', 'LT', 'EQ', 'NE', 'CONTAINS', 'NOT_CONTAINS'].includes(op)) {
    return { op, value: rest }
  }
  return null
}

export function formatCondition(parsed: ParsedCondition | null, fallback = 'GTE:0'): string {
  if (!parsed) return fallback
  if (parsed.op === 'BETWEEN') {
    return `BETWEEN:${parsed.min}:${parsed.max}`
  }
  return `${parsed.op}:${parsed.value}`
}

/** Display value for yes/no in condition editor (1/0 ↔ yes/no). */
export function conditionValueToDisplay(value: string, type: ScorecardParamType): string {
  if (type !== 'yesno') return value
  if (value === '1' || value.toUpperCase() === 'Y' || value.toUpperCase() === 'YES') return '1'
  if (value === '0' || value.toUpperCase() === 'N' || value.toUpperCase() === 'NO') return '0'
  return value
}

export function defaultConditionForParam(param: ScorecardParamDef | undefined): string {
  if (!param) return 'GTE:0'
  if (param.type === 'yesno') return 'EQ:1'
  if (param.type === 'enum' && param.enumOptions?.length) return `EQ:${param.enumOptions[0].value}`
  if (param.type === 'text') return 'EQ:'
  return 'GTE:0'
}

export function describeCondition(raw: string, param?: ScorecardParamDef): string {
  const parsed = parseCondition(raw)
  if (!parsed) return raw
  if (parsed.op === 'BETWEEN') {
    return `${OP_LABELS.BETWEEN} ${parsed.min} and ${parsed.max}`
  }
  let val = parsed.value
  if (param?.type === 'yesno') {
    val = val === '1' ? 'Yes' : val === '0' ? 'No' : val
  } else if (param?.type === 'enum' && param.enumOptions) {
    val = param.enumOptions.find((o) => o.value === parsed.value)?.label ?? val
  }
  return `${conditionOpLabel(parsed.op, param?.type)} ${val}`
}
