import type { ScorecardRow } from '@/api/scorecards'
import { paramDef } from '@/lib/credit/scorecardConfig'

export type ScorecardInputRequirement = {
  parameter: string
  source: string
  label: string
  manualKey: string
  ready: boolean
}

function readScorecardValue(scorecardMap: Record<string, string> | undefined, parameter: string): string {
  if (!scorecardMap) return ''
  const direct = scorecardMap[parameter]
  if (direct != null && String(direct).trim() !== '') return String(direct).trim()
  const upper = scorecardMap[parameter.toUpperCase()]
  if (upper != null && String(upper).trim() !== '') return String(upper).trim()
  return ''
}

function manualKeyForRow(row: ScorecardRow): string {
  const def = paramDef(row.source, row.parameter)
  return def?.manualKey ?? row.parameter
}

/** OTHER / GST rows referenced by the matched scorecard (bank statement uses server gap-fill). */
export function scorecardManualInputRequirements(
  rows: ScorecardRow[],
  scorecardMap: Record<string, string> | undefined,
): ScorecardInputRequirement[] {
  const seen = new Set<string>()
  const out: ScorecardInputRequirement[] = []
  for (const row of rows) {
    const source = String(row.source ?? '').toUpperCase()
    const parameter = String(row.parameter ?? '').trim()
    if (!parameter) continue
    if (source !== 'OTHER' && source !== 'GST_STATEMENT') continue
    const key = `${source}:${parameter}`
    if (seen.has(key)) continue
    seen.add(key)
    const manualKey = manualKeyForRow(row)
    const value = readScorecardValue(scorecardMap, parameter)
    out.push({
      parameter,
      source,
      label: paramDef(row.source, row.parameter)?.label ?? parameter,
      manualKey,
      ready: value.length > 0,
    })
  }
  return out
}

/** OTHER / bank-statement rows that still need manual values before underwriting. */
export function missingScorecardInputRequirements(
  rows: ScorecardRow[],
  scorecardMap: Record<string, string> | undefined,
): ScorecardInputRequirement[] {
  return scorecardManualInputRequirements(rows, scorecardMap).filter((r) => !r.ready)
}

export function allScorecardInputsReady(requirements: ScorecardInputRequirement[]): boolean {
  if (requirements.length === 0) return true
  return requirements.every((r) => r.ready)
}
