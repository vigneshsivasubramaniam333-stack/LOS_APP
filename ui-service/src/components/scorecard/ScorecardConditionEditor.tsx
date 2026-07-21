import { useMemo } from 'react'
import {
  conditionOpLabel,
  defaultConditionForParam,
  formatCondition,
  opsForParamType,
  parseCondition,
  type ConditionOp,
} from '@/lib/credit/scorecardCondition'
import type { ScorecardParamDef } from '@/lib/credit/scorecardConfig'

type Props = {
  value: string
  onChange: (encoded: string) => void
  paramDef?: ScorecardParamDef
  className?: string
}

export function ScorecardConditionEditor({ value, onChange, paramDef, className = '' }: Props) {
  const type = paramDef?.type ?? 'number'
  const parsed = useMemo(() => parseCondition(value) ?? parseCondition(defaultConditionForParam(paramDef)), [value, paramDef])
  const ops = opsForParamType(type)

  function updateOp(op: ConditionOp) {
    if (!parsed) return
    if (op === 'BETWEEN') {
      onChange(formatCondition({ op: 'BETWEEN', min: '0', max: '100' }))
      return
    }
    const currentVal = parsed.op === 'BETWEEN' ? '0' : parsed.value
    onChange(formatCondition({ op, value: currentVal }))
  }

  function updateValue(newVal: string) {
    if (!parsed || parsed.op === 'BETWEEN') return
    onChange(formatCondition({ op: parsed.op, value: newVal }))
  }

  function updateBetween(min: string, max: string) {
    onChange(formatCondition({ op: 'BETWEEN', min, max }))
  }

  const op = parsed?.op ?? 'GTE'

  return (
    <div className={`flex flex-wrap items-center gap-1.5 ${className}`.trim()}>
      <select
        className="bt-input bt-input-sm min-w-[9rem]"
        value={op}
        onChange={(e) => updateOp(e.target.value as ConditionOp)}
        aria-label="Condition operator"
      >
        {ops.map((o) => (
          <option key={o} value={o}>
            {conditionOpLabel(o, type)}
          </option>
        ))}
      </select>

      {op === 'BETWEEN' && parsed?.op === 'BETWEEN' ? (
        <>
          <input
            type="number"
            className="bt-input bt-input-sm w-20"
            value={parsed.min}
            onChange={(e) => updateBetween(e.target.value, parsed.max)}
            placeholder="Min"
            aria-label="Minimum value"
          />
          <span className="text-xs text-slate-500">and</span>
          <input
            type="number"
            className="bt-input bt-input-sm w-20"
            value={parsed.max}
            onChange={(e) => updateBetween(parsed.min, e.target.value)}
            placeholder="Max"
            aria-label="Maximum value"
          />
        </>
      ) : type === 'yesno' ? (
        <select
          className="bt-input bt-input-sm min-w-[5rem]"
          value={parsed && parsed.op !== 'BETWEEN' ? (parsed.value === '0' ? '0' : '1') : '1'}
          onChange={(e) => updateValue(e.target.value)}
          aria-label="Yes or no"
        >
          <option value="1">Yes</option>
          <option value="0">No</option>
        </select>
      ) : type === 'enum' && paramDef?.enumOptions?.length ? (
        <select
          className="bt-input bt-input-sm min-w-[6rem]"
          value={parsed && parsed.op !== 'BETWEEN' ? parsed.value : paramDef.enumOptions[0].value}
          onChange={(e) => updateValue(e.target.value)}
          aria-label="Enum value"
        >
          {paramDef.enumOptions.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      ) : type === 'text' ? (
        <input
          type="text"
          className="bt-input bt-input-sm w-28"
          value={parsed && parsed.op !== 'BETWEEN' ? parsed.value : ''}
          onChange={(e) => updateValue(e.target.value)}
          placeholder="Text value"
          aria-label="Text value"
        />
      ) : (
        <input
          type="number"
          className="bt-input bt-input-sm w-24"
          value={parsed && parsed.op !== 'BETWEEN' ? parsed.value : ''}
          onChange={(e) => updateValue(e.target.value)}
          placeholder="Value"
          aria-label="Threshold value"
        />
      )}
    </div>
  )
}
