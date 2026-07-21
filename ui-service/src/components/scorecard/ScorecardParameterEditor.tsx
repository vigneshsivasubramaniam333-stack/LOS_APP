import type { ScorecardParameterDef, ScorecardParamOption, ScorecardRow } from '@/api/scorecards'
import { ScorecardConditionEditor } from '@/components/scorecard/ScorecardConditionEditor'
import {
  defaultParameterForSource,
  isKnownParameter,
  paramDef,
  parametersForSource,
  scorecardSourceOptionsForLoanProduct,
  sourceDef,
  type ScorecardParamDef,
} from '@/lib/credit/scorecardConfig'
import { defaultConditionForParam } from '@/lib/credit/scorecardCondition'

const CUSTOM_PARAM = '__custom__'
const MATCH_OPTION = 'MATCH_OPTION'

type Props = {
  rows: ScorecardRow[]
  onChange: (rows: ScorecardRow[]) => void
  parameterDefs: Record<string, ScorecardParameterDef>
  onParameterDefsChange: (defs: Record<string, ScorecardParameterDef>) => void
  showAttachment?: boolean
  loanProduct?: string
}

function effectiveParamDef(source: string, row: ScorecardRow): ScorecardParamDef | undefined {
  const base = paramDef(source, row.parameter)
  const inputType = row.inputType
  if (inputType === 'dropdown') {
    return {
      value: row.parameter || 'custom',
      label: base?.label ?? row.parameter,
      type: 'enum',
      manualKey: row.parameter,
      enumOptions: (row.options ?? []).map((o) => ({ value: o.value, label: o.label })),
    }
  }
  if (inputType === 'text') {
    return {
      value: row.parameter || 'custom',
      label: base?.label ?? row.parameter,
      type: 'text',
      manualKey: row.parameter,
    }
  }
  return base
}

/** Rebuild parameterDefs map from custom OTHER rows (for persistence). */
export function buildParameterDefsFromRows(rows: ScorecardRow[]): Record<string, ScorecardParameterDef> {
  const out: Record<string, ScorecardParameterDef> = {}
  for (const row of rows) {
    const name = row.parameter?.trim()
    if (!name) continue
    if (!sourceDef(row.source)?.allowCustomParameter) continue
    if (isKnownParameter(row.source, name)) continue
    const inputType = row.inputType ?? 'number'
    out[name] = {
      inputType,
      ...(inputType === 'dropdown' && row.options?.length ? { options: row.options } : {}),
      ...(inputType === 'dropdown' && !row.options?.length
        ? { options: [{ value: 'OPTION_1', label: 'Option 1', score: 0 }] }
        : {}),
    }
  }
  return out
}

export function ScorecardParameterEditor({
  rows,
  onChange,
  parameterDefs: _parameterDefs,
  onParameterDefsChange,
  showAttachment = true,
  loanProduct = '',
}: Props) {
  const sourceOptions = scorecardSourceOptionsForLoanProduct(loanProduct || 'PERSONAL_LOAN')

  function syncDefs(nextRows: ScorecardRow[]) {
    onParameterDefsChange(buildParameterDefsFromRows(nextRows))
  }

  function updateRows(next: ScorecardRow[]) {
    onChange(next)
    syncDefs(next)
  }

  function updateRow(index: number, patch: Partial<ScorecardRow>) {
    const next = rows.map((r, i) => (i === index ? { ...r, ...patch } : r))
    updateRows(next)
  }

  function onSourceChange(index: number, source: string) {
    const param = defaultParameterForSource(source)
    const pDef = paramDef(source, param)
    updateRow(index, {
      source,
      parameter: param,
      condition: defaultConditionForParam(pDef),
      inputType: undefined,
      options: undefined,
    })
  }

  function onParameterChange(index: number, source: string, raw: string) {
    if (raw === CUSTOM_PARAM) {
      updateRow(index, {
        parameter: '',
        condition: 'GTE:0',
        inputType: 'number',
        options: undefined,
      })
      return
    }
    const pDef = paramDef(source, raw)
    updateRow(index, {
      parameter: raw,
      condition: defaultConditionForParam(pDef),
      inputType: undefined,
      options: undefined,
    })
  }

  function onCustomInputTypeChange(index: number, inputType: ScorecardParameterDef['inputType']) {
    const row = rows[index]
    if (inputType === 'dropdown') {
      updateRow(index, {
        inputType,
        options: row.options?.length ? row.options : [{ value: 'OPTION_1', label: 'Option 1', score: 0 }],
        condition: MATCH_OPTION,
        score: 0,
      })
      return
    }
    if (inputType === 'text') {
      updateRow(index, {
        inputType,
        options: undefined,
        condition: 'EQ:',
        score: row.score || 20,
      })
      return
    }
    updateRow(index, {
      inputType: 'number',
      options: undefined,
      condition: 'GTE:0',
      score: row.score || 20,
    })
  }

  function updateOptions(index: number, options: ScorecardParamOption[]) {
    updateRow(index, { options })
  }

  return (
    <div className="space-y-2">
      <p className="text-xs text-slate-600">
        For <strong>Other (manual)</strong> → <strong>Custom parameter</strong>, choose input type{' '}
        <strong>Number</strong>, <strong>Text</strong> (matches / contains), or <strong>Dropdown</strong> (option
        scores via MATCH_OPTION).
      </p>
      <div className="overflow-x-auto">
        <table className="bt-table min-w-[56rem] w-full">
          <colgroup>
            <col className="w-[10rem]" />
            <col className="w-[14rem]" />
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
              <th>Condition / options</th>
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
              const selectValue = known || !row.parameter ? row.parameter || CUSTOM_PARAM : CUSTOM_PARAM
              const isCustomMode = Boolean(allowCustom && selectValue === CUSTOM_PARAM)
              const inputType = row.inputType ?? (isCustomMode ? 'number' : undefined)
              const optionScored = isCustomMode && inputType === 'dropdown'
              const pDef = effectiveParamDef(src, row)

              return (
                <tr key={row.id}>
                  <td>
                    <select
                      className="bt-input bt-input-sm w-full min-w-[8rem]"
                      value={src}
                      onChange={(e) => onSourceChange(i, e.target.value)}
                    >
                      {sourceOptions.map((s) => (
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
                    {isCustomMode ? (
                      <div className="mt-1 space-y-1">
                        <input
                          className="bt-input bt-input-sm w-full font-mono text-xs"
                          value={row.parameter}
                          onChange={(e) => updateRow(i, { parameter: e.target.value.trim() })}
                          placeholder="Parameter code (e.g. OccupationTier)"
                        />
                        <label className="block text-[11px] font-medium text-slate-700">
                          Input type
                          <select
                            className="bt-input bt-input-sm mt-0.5 w-full border-amber-300 bg-amber-50"
                            value={inputType ?? 'number'}
                            onChange={(e) =>
                              onCustomInputTypeChange(i, e.target.value as ScorecardParameterDef['inputType'])
                            }
                          >
                            <option value="number">Number</option>
                            <option value="text">Text</option>
                            <option value="dropdown">Dropdown</option>
                          </select>
                        </label>
                      </div>
                    ) : null}
                  </td>
                  <td>
                    {optionScored ? (
                      <div className="space-y-2 rounded border border-amber-200 bg-amber-50/50 p-2">
                        <p className="text-[10px] text-amber-900">
                          Score comes from the matched option (saved as MATCH_OPTION).
                        </p>
                        {(row.options ?? []).map((opt, idx) => (
                          <div key={idx} className="flex flex-wrap items-end gap-1">
                            <input
                              className="bt-input bt-input-sm w-24"
                              placeholder="Label"
                              value={opt.label}
                              onChange={(e) => {
                                const options = [...(row.options ?? [])]
                                options[idx] = { ...opt, label: e.target.value }
                                updateOptions(i, options)
                              }}
                            />
                            <input
                              className="bt-input bt-input-sm w-24 font-mono text-xs"
                              placeholder="Value"
                              value={opt.value}
                              onChange={(e) => {
                                const options = [...(row.options ?? [])]
                                options[idx] = { ...opt, value: e.target.value }
                                updateOptions(i, options)
                              }}
                            />
                            <input
                              type="number"
                              className="bt-input bt-input-sm w-16"
                              placeholder="Score"
                              title="Score for this option"
                              value={opt.score}
                              onChange={(e) => {
                                const options = [...(row.options ?? [])]
                                options[idx] = { ...opt, score: Number.parseInt(e.target.value, 10) || 0 }
                                updateOptions(i, options)
                              }}
                            />
                            <button
                              type="button"
                              className="text-[10px] text-rose-700"
                              onClick={() =>
                                updateOptions(
                                  i,
                                  (row.options ?? []).filter((_, j) => j !== idx),
                                )
                              }
                            >
                              Remove
                            </button>
                          </div>
                        ))}
                        <button
                          type="button"
                          className="rounded border border-slate-300 bg-white px-2 py-0.5 text-[10px]"
                          onClick={() =>
                            updateOptions(i, [
                              ...(row.options ?? []),
                              {
                                value: `OPTION_${(row.options?.length ?? 0) + 1}`,
                                label: 'New option',
                                score: 0,
                              },
                            ])
                          }
                        >
                          Add option
                        </button>
                      </div>
                    ) : (
                      <ScorecardConditionEditor
                        value={row.condition}
                        onChange={(condition) => updateRow(i, { condition })}
                        paramDef={pDef}
                      />
                    )}
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
                    {optionScored ? (
                      <span className="text-[10px] text-slate-500">From options</span>
                    ) : (
                      <input
                        type="number"
                        className="bt-input w-20"
                        value={row.score}
                        onChange={(e) => updateRow(i, { score: Number(e.target.value) || 0 })}
                      />
                    )}
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
                      onClick={() => updateRows(rows.filter((_, j) => j !== i))}
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
    </div>
  )
}
