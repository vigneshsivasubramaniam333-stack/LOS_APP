import { AppSectionCard } from '@/components/ui/AppSectionCard'
import {
  buildScorecardCategoryGroups,
  type ScorecardParameterRow,
} from '@/lib/credit/scorecardSummaryLayout'

type EvalShape = {
  aggregateScore?: number
  aggregateDecision?: string
  scorecardName?: string
  scorecardVersion?: number
  parameterResults?: ScorecardParameterRow[]
}

function outcomeBadgeClass(decision: string | undefined): string {
  const d = (decision ?? '').toUpperCase()
  if (d === 'APPROVED' || d === 'APPROVE') {
    return 'bg-emerald-100 text-emerald-900 border-emerald-200'
  }
  if (d === 'MANUAL_REVIEW' || d === 'MANUAL') {
    return 'bg-amber-100 text-amber-950 border-amber-200'
  }
  if (d === 'REJECTED' || d === 'REJECT') {
    return 'bg-rose-100 text-rose-950 border-rose-200'
  }
  return 'bg-slate-100 text-slate-800 border-slate-200'
}

function statusBadgeClass(status: string): string {
  if (status === 'pass') return 'bg-emerald-100 text-emerald-900 border-emerald-200'
  if (status === 'missing') return 'bg-amber-100 text-amber-950 border-amber-200'
  if (status === 'fail') return 'bg-rose-100 text-rose-950 border-rose-200'
  return 'bg-slate-100 text-slate-700 border-slate-200'
}

export function ScorecardSummaryPanel({ evaluation }: { evaluation: EvalShape }) {
  const rows = evaluation.parameterResults
  const groups = buildScorecardCategoryGroups(rows)
  const aggregate = evaluation.aggregateScore

  if (aggregate == null && groups.length === 0) {
    return null
  }

  const titleBits = [
    evaluation.scorecardName,
    evaluation.scorecardVersion != null ? `v${evaluation.scorecardVersion}` : null,
  ].filter(Boolean)

  return (
    <AppSectionCard
      tone="violet"
      title="Application credit score"
      subtitle={
        titleBits.length > 0 ? (
          <span className="text-slate-600">{titleBits.join(' · ')}</span>
        ) : undefined
      }
      badge={
        evaluation.aggregateDecision ? (
          <span
            className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-medium ${outcomeBadgeClass(evaluation.aggregateDecision)}`}
          >
            {String(evaluation.aggregateDecision).replace(/_/g, ' ')}
          </span>
        ) : null
      }
    >
      <div className="flex flex-col gap-6">
        {aggregate != null ? (
          <div className="flex flex-wrap items-end gap-4 border-b border-violet-200/60 pb-5">
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-violet-800/80">Composite score</p>
              <p className="text-5xl font-semibold tabular-nums leading-none text-violet-950">{aggregate}</p>
            </div>
            <p className="max-w-md text-sm text-slate-600">
              Normalized score from the matched underwriting scorecard. Each criterion below shows whether it passed,
              failed, or was missing, and how many points it contributed.
            </p>
          </div>
        ) : null}

        {groups.length > 0 ? (
          <div className="space-y-6">
            <div>
              <h4 className="text-sm font-semibold text-slate-900">Overview</h4>
              <div className="mt-2 overflow-x-auto rounded-lg border border-slate-200 bg-white">
                <table className="w-full min-w-[320px] border-collapse text-left text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                      <th className="px-4 py-2.5 font-medium">Category</th>
                      <th className="px-4 py-2.5 text-right font-medium">Points earned</th>
                      <th className="px-4 py-2.5 text-right font-medium">Normalized %</th>
                    </tr>
                  </thead>
                  <tbody>
                    {groups.map((g) => {
                      const maxPts = g.rows.reduce((sum, r) => sum + (r.maxScore ?? 0), 0)
                      const norm = maxPts > 0 ? Math.round((g.categoryScore / maxPts) * 1000) / 10 : 0
                      return (
                        <tr key={g.category} className="border-b border-slate-100 last:border-0">
                          <td className="px-4 py-2.5 font-medium text-slate-900">{g.category}</td>
                          <td className="px-4 py-2.5 text-right font-mono tabular-nums text-slate-900">
                            {g.categoryScore.toFixed(0)}
                            {maxPts > 0 ? ` / ${maxPts.toFixed(0)}` : ''}
                          </td>
                          <td className="px-4 py-2.5 text-right font-mono tabular-nums text-slate-700">
                            {norm.toFixed(1)}%
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>

            {groups.map((g) => (
              <div key={g.category} className="space-y-2">
                <h4 className="text-sm font-semibold text-violet-950">Category: {g.category}</h4>
                <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
                  <table className="w-full min-w-[900px] border-collapse text-left text-xs">
                    <thead>
                      <tr className="border-b border-slate-200 bg-slate-50 text-[11px] uppercase tracking-wide text-slate-500">
                        <th className="w-[16%] px-3 py-2 font-medium">Criterion</th>
                        <th className="w-[10%] px-3 py-2 font-medium">Status</th>
                        <th className="w-[10%] px-3 py-2 font-medium">Value used</th>
                        <th className="w-[14%] px-3 py-2 font-medium">Source</th>
                        <th className="w-[8%] px-3 py-2 text-right font-medium">Weight</th>
                        <th className="w-[10%] px-3 py-2 text-right font-medium">Points</th>
                        <th className="w-[8%] px-3 py-2 text-right font-medium">Score %</th>
                        <th className="w-[24%] px-3 py-2 font-medium">Scoring impact</th>
                      </tr>
                    </thead>
                    <tbody>
                      {g.rows.map((r, idx) => (
                        <tr key={`${r.row.rowId ?? r.criterion}-${idx}`} className="border-b border-slate-100 last:border-0">
                          <td className="px-3 py-2 align-top font-medium text-slate-900">{r.criterion}</td>
                          <td className="px-3 py-2 align-top">
                            <span
                              className={`inline-flex rounded border px-1.5 py-0.5 text-[10px] font-medium ${statusBadgeClass(r.status)}`}
                            >
                              {r.statusLabel}
                            </span>
                          </td>
                          <td className="px-3 py-2 align-top font-mono text-[11px] text-slate-800">{r.valueUsed}</td>
                          <td className="px-3 py-2 align-top text-[11px] text-slate-600 [overflow-wrap:anywhere]">
                            {r.valueSource}
                          </td>
                          <td className="px-3 py-2 align-top text-right font-mono tabular-nums text-slate-700">
                            {r.weightPercent > 0 ? `${r.weightPercent.toFixed(1)}%` : '—'}
                          </td>
                          <td className="px-3 py-2 align-top text-right font-mono tabular-nums text-slate-900">
                            {r.pointsEarned != null && r.maxScore != null
                              ? `${r.pointsEarned} / ${r.maxScore}`
                              : '—'}
                          </td>
                          <td className="px-3 py-2 align-top text-right font-mono tabular-nums text-slate-900">
                            {r.score.toFixed(1)}
                          </td>
                          <td className="px-3 py-2 align-top text-slate-600">{r.description}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </AppSectionCard>
  )
}
