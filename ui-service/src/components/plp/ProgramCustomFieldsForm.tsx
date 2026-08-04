import { useEffect, useState } from 'react'
import type { ProgramFieldDefinition } from '@/types/plp'

/**
 * Dynamic program custom fields from definition catalog.
 * Values are string form state; parent converts to typed customFields map on submit.
 */
export function ProgramCustomFieldsForm({
  definitions,
  values,
  onChange,
  disabled,
  loading,
}: {
  definitions: ProgramFieldDefinition[]
  values: Record<string, string>
  onChange: (next: Record<string, string>) => void
  disabled?: boolean
  loading?: boolean
}) {
  if (loading) {
    return <p className="text-sm text-slate-500">Loading program fields…</p>
  }
  if (!definitions.length) {
    return null
  }

  function setKey(key: string, value: string) {
    onChange({ ...values, [key]: value })
  }

  return (
    <div className="space-y-3 sm:col-span-2">
      <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
        Program custom / eligibility fields
      </h4>
      <div className="grid gap-3 sm:grid-cols-2">
        {definitions.map((d) => {
          const val = values[d.fieldKey] ?? ''
          const label = (
            <>
              {d.label}
              {d.required ? <span className="text-rose-600"> *</span> : null}
            </>
          )
          if (d.inputType === 'DROPDOWN') {
            return (
              <label key={d.id} className="block text-sm font-medium text-slate-700">
                {label}
                <select
                  className="mt-1 bt-input w-full text-sm"
                  value={val}
                  onChange={(e) => setKey(d.fieldKey, e.target.value)}
                  disabled={disabled}
                  required={d.required}
                >
                  <option value="">Select</option>
                  {(d.options ?? []).map((o) => {
                    const v = String(o.value ?? '')
                    const lab = String(o.label ?? v)
                    return (
                      <option key={v} value={v}>
                        {lab}
                      </option>
                    )
                  })}
                </select>
                {d.helpText ? <span className="mt-0.5 block text-xs font-normal text-slate-500">{d.helpText}</span> : null}
              </label>
            )
          }
          if (d.inputType === 'NUMBER') {
            return (
              <label key={d.id} className="block text-sm font-medium text-slate-700">
                {label}
                <input
                  type="number"
                  className="mt-1 bt-input w-full text-sm"
                  value={val}
                  onChange={(e) => setKey(d.fieldKey, e.target.value)}
                  disabled={disabled}
                  required={d.required}
                />
                {d.helpText ? <span className="mt-0.5 block text-xs font-normal text-slate-500">{d.helpText}</span> : null}
              </label>
            )
          }
          return (
            <label key={d.id} className="block text-sm font-medium text-slate-700">
              {label}
              <input
                type="text"
                className="mt-1 bt-input w-full text-sm"
                value={val}
                onChange={(e) => setKey(d.fieldKey, e.target.value)}
                disabled={disabled}
                required={d.required}
              />
              {d.helpText ? <span className="mt-0.5 block text-xs font-normal text-slate-500">{d.helpText}</span> : null}
            </label>
          )
        })}
      </div>
    </div>
  )
}

export function validateCustomFieldValues(
  definitions: ProgramFieldDefinition[],
  values: Record<string, string>,
): string | null {
  for (const d of definitions) {
    if (!d.required) continue
    const v = (values[d.fieldKey] ?? '').trim()
    if (!v) return `${d.label} is required.`
  }
  return null
}

export function customFieldsMapFromForm(
  definitions: ProgramFieldDefinition[],
  values: Record<string, string>,
): Record<string, string | number> {
  const out: Record<string, string | number> = {}
  for (const d of definitions) {
    const raw = (values[d.fieldKey] ?? '').trim()
    if (!raw) continue
    if (d.inputType === 'NUMBER') {
      const n = Number(raw)
      if (Number.isFinite(n)) out[d.fieldKey] = Number.isInteger(n) ? Math.trunc(n) : n
    } else {
      out[d.fieldKey] = raw
    }
  }
  return out
}

export function formValuesFromProgram(
  definitions: ProgramFieldDefinition[],
  program: {
    customFields?: Record<string, unknown> | null
    tenureDays?: number | null
    anchorRelationshipVintageMonths?: number | null
    interestPayment?: string | null
    maxInvoiceVintageDays?: number | null
    maxCmr?: number | null
    minCibil?: number | null
  } | null,
): Record<string, string> {
  const out: Record<string, string> = {}
  if (!program) return out
  const cf = program.customFields ?? {}
  for (const d of definitions) {
    const fromCf =
      cf[d.fieldKey] ??
      (d.fieldKey === 'maxInvoiceAgeDays' ? cf.maxInvoiceVintageDays : undefined) ??
      (d.fieldKey === 'maxInvoiceVintageDays' ? cf.maxInvoiceAgeDays : undefined) ??
      (d.fieldKey === 'maxTenureDays' ? cf.tenureDays : undefined) ??
      (d.fieldKey === 'tenureDays' ? cf.maxTenureDays : undefined)
    if (fromCf != null && String(fromCf).trim() !== '') {
      out[d.fieldKey] = String(fromCf)
      continue
    }
    // Legacy scalar fallback for known keys (LOS columns) and PLP catalog key names
    switch (d.fieldKey) {
      case 'tenureDays':
      case 'maxTenureDays':
        if (program.tenureDays != null) out[d.fieldKey] = String(program.tenureDays)
        break
      case 'anchorRelationshipVintageMonths':
        if (program.anchorRelationshipVintageMonths != null)
          out[d.fieldKey] = String(program.anchorRelationshipVintageMonths)
        break
      case 'interestPayment':
        if (program.interestPayment) out[d.fieldKey] = program.interestPayment
        break
      case 'maxInvoiceVintageDays':
      case 'maxInvoiceAgeDays':
        if (program.maxInvoiceVintageDays != null) out[d.fieldKey] = String(program.maxInvoiceVintageDays)
        break
      case 'maxCmr':
        if (program.maxCmr != null) out[d.fieldKey] = String(program.maxCmr)
        break
      case 'minCibil':
        if (program.minCibil != null) out[d.fieldKey] = String(program.minCibil)
        break
      default:
        break
    }
  }
  return out
}

/** Hook-friendly loader: keep import of useEffect here for simple forms if needed. */
export function useProgramFieldDefinitions(productType: string | undefined) {
  const [definitions, setDefinitions] = useState<ProgramFieldDefinition[]>([])
  const [loading, setLoading] = useState(false)
  useEffect(() => {
    let cancelled = false
    if (!productType) {
      setDefinitions([])
      return
    }
    setLoading(true)
    import('@/api/plp')
      .then(({ listProgramFieldDefinitions }) =>
        listProgramFieldDefinitions({ productType, activeOnly: true }),
      )
      .then((rows) => {
        if (!cancelled) setDefinitions(rows)
      })
      .catch(() => {
        if (!cancelled) setDefinitions([])
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [productType])
  return { definitions, loading }
}
