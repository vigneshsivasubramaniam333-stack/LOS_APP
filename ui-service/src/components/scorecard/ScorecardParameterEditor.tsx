import type { ScorecardRow } from '@/api/scorecards'
import { ScorecardConditionEditor } from '@/components/scorecard/ScorecardConditionEditor'
import {
  defaultParameterForSource,
  isKnownParameter,
  paramDef,
  parametersForSource,
  SCORECARD_SOURCE_OPTIONS,
  sourceDef,
} from '@/lib/credit/scorecardConfig'
import { defaultConditionForParam } from '@/lib/credit/scorecardCondition'

const CUSTOM_PARAM = '__custom__'

type Props = {
  rows: ScorecardRow[]
  onChange: (rows: ScorecardRow[]) => void
  showAttachment?: boolean
}

export function ScorecardParameterEditor({ rows, onChange, showAttachment = true }: Props) {
  function updateRow(index: number, patch: Partial<ScorecardRow>) {
    onChange(rows.map((r, i) => (i === index ? { ...r, ...patch } : r)))
  }

  function onSourceChange(index: number, source: string) {
    const param = defaultParameterForSource(source)
    const pDef = paramDef(source, param)
    updateRow(index, {
      source,
      parameter: param,
      condition: defaultConditionForParam(pDef),
    })
  }

  function onParameterChange(index: number, source: string, raw: string) {
    if (raw === CUSTOM_PARAM) {
      updateRow(index, { parameter: '' })
      return
    }
    const pDef = paramDef(source, raw)
    updateRow(index, { parameter: raw, condition: defaultConditionForParam(pDef) })
  }

  return (
    <div className="overflow-x-auto">
      <table className="bt-table min-w-[56rem] w-full">
        <colgroup>
          <col className="w-[10rem]" />
          <col className="w-[12rem]" />
          <col className="w-[18rem]" />
          <col className="w-[6rem]" />
          <col className="w-[6rem]" />
          {showAttachment ? <col className="w-[10rem]" /> : null}
          <col className="w-[2.5rem]" />
        </colgroup>
        <thead>
          <tr>
            <th>Source</th>
            <th>Parameter</th>
            <th>Condition</th>
            <th className="w-24 whitespace-nowrap">Weight</th>
            <th className="w-24 whitespace-nowrap">Points</th>
            {showAttachment ? <th>Attachment</th> : null}
            <th className="w-10" />
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => {
            const src = row.source
            const params = parametersForSource(src)
            const allowCustom = sourceDef(src)?.allowCustomParameter
            const known = isKnownParameter(src, row.parameter)
            const pDef = paramDef(src, row.parameter)
            const selectValue = known || !row.parameter ? row.parameter || CUSTOM_PARAM : CUSTOM_PARAM

            return (
              <tr key={row.id}>
                <td>
                  <select
                    className="bt-input bt-input-sm w-full min-w-[8rem]"
                    value={src}
                    onChange={(e) => onSourceChange(i, e.target.value)}
                  >
                    {SCORECARD_SOURCE_OPTIONS.map((s) => (
                      <option key={s.value} value={s.value}>
                        {s.label}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <select
                    className="bt-input bt-input-sm w-full min-w-[10rem]"
                    value={selectValue}
                    onChange={(e) => onParameterChange(i, src, e.target.value)}
                  >
                    {params.map((p) => (
                      <option key={p.value} value={p.value}>
                        {p.label}
                      </option>
                    ))}
                    {allowCustom ? <option value={CUSTOM_PARAM}>— Custom parameter —</option> : null}
                  </select>
                  {allowCustom && selectValue === CUSTOM_PARAM ? (
                    <input
                      className="bt-input bt-input-sm mt-1 w-full font-mono text-xs"
                      value={row.parameter}
                      onChange={(e) => updateRow(i, { parameter: e.target.value.trim() })}
                      placeholder="e.g. customFieldName"
                    />
                  ) : null}
                </td>
                <td>
                  <ScorecardConditionEditor
                    value={row.condition}
                    onChange={(condition) => updateRow(i, { condition })}
                    paramDef={pDef}
                  />
                </td>
                <td className="w-24 overflow-visible">
                  <input
                    type="number"
                    className="bt-input w-20"
                    value={row.weight}
                    onChange={(e) => updateRow(i, { weight: Number(e.target.value) || 0 })}
                  />
                </td>
                <td className="w-24 overflow-visible">
                  <input
                    type="number"
                    className="bt-input w-20"
                    value={row.score}
                    onChange={(e) => updateRow(i, { score: Number(e.target.value) || 0 })}
                  />
                </td>
                {showAttachment ? (
                  <td>
                    <input
                      className="bt-input bt-input-sm w-full min-w-[7rem]"
                      value={row.attachment ?? ''}
                      onChange={(e) => updateRow(i, { attachment: e.target.value })}
                      placeholder="e.g. BUREAU_REPORT"
                    />
                  </td>
                ) : null}
                <td>
                  <button
                    type="button"
                    className="bt-btn-icon text-rose-600"
                    onClick={() => onChange(rows.filter((_, j) => j !== i))}
                    title="Remove row"
                  >
                    ×
                  </button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
