import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  createScorecard,
  deleteScorecard,
  listScorecards,
  updateScorecard,
  type HardRuleRow,
  type ScorecardRow,
  type UnderwritingScorecardRequest,
  type UnderwritingScorecardResponse,
} from '@/api/scorecards'
import { ApiError } from '@/api/http'
import { ScorecardParameterEditor } from '@/components/scorecard/ScorecardParameterEditor'
import { ScorecardConditionEditor } from '@/components/scorecard/ScorecardConditionEditor'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import {
  BtAlert,
  DetailActions,
  DetailEmptyState,
  DetailPanel,
  DetailSection,
  FormField,
  MasterDetailLayout,
  MasterListItem,
  MasterListPanel,
} from '@/components/ui/AdminLayout'
import { BORROWER_TYPE_LABELS, BORROWER_TYPE_ORDER } from '@/catalog/borrowerTypes'
import { isLoanProductCode, LOAN_PRODUCT_CODES, LOAN_PRODUCT_LABELS, loanProductLabel } from '@/catalog/loanProducts'
import {
  defaultParameterForSource,
  paramDef,
  parametersForSource,
  SCORECARD_SOURCE_OPTIONS,
} from '@/lib/credit/scorecardConfig'
import { defaultConditionForParam } from '@/lib/credit/scorecardCondition'
import type { BorrowerType } from '@/types/createApplication'

const BORROWER_TYPES: BorrowerType[] = [...BORROWER_TYPE_ORDER]

let rid = 0
function newRow(): ScorecardRow {
  rid += 1
  const source = 'BUREAU'
  const parameter = defaultParameterForSource(source)
  return {
    id: `r${Date.now()}-${rid}`,
    parameter,
    source,
    condition: defaultConditionForParam(paramDef(source, parameter)),
    weight: 1,
    score: 20,
  }
}

function newHard(): HardRuleRow {
  rid += 1
  const source = 'BUREAU'
  const parameter = defaultParameterForSource(source)
  return {
    id: `h${Date.now()}-${rid}`,
    parameter,
    source,
    condition: 'LT:500',
    decision: 'REJECT',
  }
}

export function ScorecardsPage() {
  const [rows, setRows] = useState<UnderwritingScorecardResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<UnderwritingScorecardResponse | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [listSearch, setListSearch] = useState('')

  const [name, setName] = useState('')
  const [borrowerType, setBorrowerType] = useState<BorrowerType>('INDIVIDUAL')
  const [loanProduct, setLoanProduct] = useState('PERSONAL_LOAN')
  const [version, setVersion] = useState(1)
  const [priority, setPriority] = useState(200)
  const [minAmount, setMinAmount] = useState('')
  const [maxAmount, setMaxAmount] = useState('')
  const [geoState, setGeoState] = useState('')
  const [geoCity, setGeoCity] = useState('')
  const [active, setActive] = useState(true)
  const [approveMin, setApproveMin] = useState(70)
  const [manualMin, setManualMin] = useState(40)
  const [grid, setGrid] = useState<ScorecardRow[]>([newRow()])
  const [hards, setHards] = useState<HardRuleRow[]>([])

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const s = await listScorecards()
      setRows(s)
    } catch (e) {
      setRows(null)
      setLoadError(e instanceof Error ? e.message : 'Failed to load')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const filteredRows = useMemo(() => {
    const items = rows ?? []
    const q = listSearch.trim().toLowerCase()
    if (!q) return items
    return items.filter((r) => {
      const borrowerLabel = (BORROWER_TYPE_LABELS[r.borrowerType as BorrowerType] ?? r.borrowerType).toLowerCase()
      const productLabel = loanProductLabel(r.loanProduct).toLowerCase()
      return (
        r.name.toLowerCase().includes(q)
        || r.borrowerType.toLowerCase().includes(q)
        || borrowerLabel.includes(q)
        || r.loanProduct.toLowerCase().includes(q)
        || productLabel.includes(q)
        || String(r.priority).includes(q)
      )
    })
  }, [rows, listSearch])

  function apply(r: UnderwritingScorecardResponse) {
    setSelected(r)
    setIsCreating(false)
    setName(r.name)
    setBorrowerType(r.borrowerType as BorrowerType)
    setLoanProduct(r.loanProduct)
    setVersion(r.version)
    setPriority(r.priority)
    setMinAmount(r.minAmount != null ? String(r.minAmount) : '')
    setMaxAmount(r.maxAmount != null ? String(r.maxAmount) : '')
    const g = r.geography
    setGeoState(g && typeof g.state === 'string' ? g.state : '')
    setGeoCity(g && typeof g.city === 'string' ? g.city : '')
    setActive(r.active)
    const t = r.thresholdsJson || {}
    setApproveMin(typeof t.approveMinPercent === 'number' ? t.approveMinPercent : 70)
    setManualMin(typeof t.manualMinPercent === 'number' ? t.manualMinPercent : 40)
    const sc = r.scorecardJson || {}
    const rawRows = (sc as { rows?: unknown }).rows
    if (Array.isArray(rawRows) && rawRows.length) {
      setGrid(
        (rawRows as Record<string, unknown>[]).map((x, i) => ({
          id: String(x.id ?? `e${i}`),
          parameter: String(x.parameter ?? 'BUREAU_SCORE'),
          source: String(x.source ?? 'BUREAU'),
          condition: String(x.condition ?? 'GTE:650'),
          weight: Number(x.weight) || 1,
          score: Number(x.score) || 0,
          attachment: x.attachment != null ? String(x.attachment) : undefined,
        })),
      )
    } else {
      setGrid([newRow()])
    }
    const hr = (r.hardRulesJson as { rules?: unknown })?.rules
    if (Array.isArray(hr) && hr.length) {
      setHards(
        (hr as Record<string, unknown>[]).map((x, i) => ({
          id: String(x.id ?? `h${i}`),
          parameter: String(x.parameter ?? 'BUREAU_SCORE'),
          source: String(x.source ?? 'BUREAU'),
          condition: String(x.condition ?? 'LT:500'),
          decision: String(x.decision) === 'MANUAL_REVIEW' ? 'MANUAL_REVIEW' : 'REJECT',
          message: x.message != null ? String(x.message) : undefined,
        })),
      )
    } else {
      setHards([])
    }
    setActionError(null)
  }

  function startNew() {
    setSelected(null)
    setIsCreating(true)
    setName('New scorecard')
    setBorrowerType('INDIVIDUAL')
    setLoanProduct(LOAN_PRODUCT_CODES[0] ?? 'PERSONAL_LOAN')
    setVersion(1)
    setPriority(200)
    setMinAmount('')
    setMaxAmount('')
    setGeoState('')
    setGeoCity('')
    setActive(true)
    setApproveMin(70)
    setManualMin(40)
    setGrid([newRow(), newRow()])
    setHards([newHard()])
    setActionError(null)
  }

  function toRequest(): UnderwritingScorecardRequest {
    const minA = minAmount.trim() ? Number.parseFloat(minAmount) : null
    const maxA = maxAmount.trim() ? Number.parseFloat(maxAmount) : null
    const geo: Record<string, unknown> | null =
      geoState.trim() || geoCity.trim()
        ? { ...(geoState.trim() ? { state: geoState.trim() } : {}), ...(geoCity.trim() ? { city: geoCity.trim() } : {}) }
        : null
    return {
      name: name.trim() || 'Scorecard',
      borrowerType,
      loanProduct: loanProduct.trim(),
      version: Math.max(1, version),
      priority,
      minAmount: minA != null && !Number.isNaN(minA) ? minA : null,
      maxAmount: maxA != null && !Number.isNaN(maxA) ? maxA : null,
      geography: geo,
      scorecardJson: {
        rows: grid.map((r) => ({
          id: r.id,
          parameter: r.parameter,
          source: r.source,
          condition: r.condition.trim(),
          weight: r.weight,
          score: r.score,
          ...(r.attachment?.trim() ? { attachment: r.attachment.trim() } : {}),
        })),
      },
      thresholdsJson: { approveMinPercent: approveMin, manualMinPercent: manualMin },
      hardRulesJson: {
        rules: hards.map((h) => ({
          parameter: h.parameter,
          source: h.source,
          condition: h.condition,
          decision: h.decision,
          ...(h.message?.trim() ? { message: h.message.trim() } : {}),
        })),
      },
      active,
    }
  }

  async function onSave() {
    setActionError(null)
    setSaving(true)
    try {
      const body = toRequest()
      if (isCreating) {
        const c = await createScorecard(body)
        setIsCreating(false)
        setSelected(c)
        setRows(await listScorecards())
        apply(c)
      } else if (selected) {
        const u = await updateScorecard(selected.id, body)
        setSelected(u)
        setRows(await listScorecards())
        apply(u)
      }
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  async function onDelete() {
    if (!selected) return
    if (!globalThis.confirm('Delete this scorecard? It must be inactive first.')) return
    setActionError(null)
    try {
      await deleteScorecard(selected.id)
      setSelected(null)
      setIsCreating(false)
      setRows(await listScorecards())
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Delete failed')
    }
  }

  const showForm = selected !== null || isCreating

  return (
    <div>
      <PageHeader
        title="Underwriting scorecards"
        description="Structured parameter scoring with hard rules and approval thresholds. Matching scorecards run before legacy rule sets (higher priority wins)."
      />

      {loading && <LoadingState label="Loading scorecards…" />}
      {loadError && <ErrorState message={loadError} />}

      {rows && !loading && (
        <MasterDetailLayout>
          <MasterListPanel
            title="Scorecards"
            count={filteredRows.length}
            search={listSearch}
            onSearchChange={setListSearch}
            searchPlaceholder="Search scorecards…"
            action={
              <button type="button" onClick={startNew} className="bt-btn bt-btn-primary bt-btn-sm">
                New scorecard
              </button>
            }
            empty={
              filteredRows.length === 0 && !isCreating ? (
                <div className="bt-master-list-empty">
                  {rows.length === 0 ? 'No scorecards yet. Create one to get started.' : 'No scorecards match your search.'}
                </div>
              ) : undefined
            }
          >
            {filteredRows.map((r) => (
              <MasterListItem
                key={r.id}
                active={selected?.id === r.id && !isCreating}
                onClick={() => apply(r)}
                avatar={r.name}
                title={r.name}
                subtitle={`Priority ${r.priority} · v${r.version}`}
                meta={
                  r.active ? (
                    <span className="bt-badge bt-badge-green">Active</span>
                  ) : (
                    <span className="bt-badge bt-badge-gray">Inactive</span>
                  )
                }
                tags={
                  <>
                    <span className="bt-tag">{BORROWER_TYPE_LABELS[r.borrowerType as BorrowerType] ?? r.borrowerType}</span>
                    <span className="bt-tag">{loanProductLabel(r.loanProduct)}</span>
                  </>
                }
              />
            ))}
          </MasterListPanel>

          <div>
            {showForm ? (
              <DetailPanel
                title={isCreating ? 'New scorecard' : name}
                description="Configure parameters, hard rules, and decision thresholds for this segment."
                badge={
                  !isCreating && selected ? (
                    selected.active ? (
                      <span className="bt-badge bt-badge-green">Active</span>
                    ) : (
                      <span className="bt-badge bt-badge-gray">Inactive</span>
                    )
                  ) : undefined
                }
                footer={
                  <DetailActions>
                    <button type="button" onClick={() => void onSave()} disabled={saving} className="bt-btn bt-btn-primary">
                      {saving ? 'Saving…' : isCreating ? 'Create scorecard' : 'Save changes'}
                    </button>
                    {selected && !isCreating ? (
                      <button type="button" onClick={() => void onDelete()} className="bt-btn bt-btn-secondary text-rose-700">
                        Delete
                      </button>
                    ) : null}
                    {!isCreating && selected ? (
                      <button type="button" onClick={startNew} className="bt-btn bt-btn-ghost">
                        New instead
                      </button>
                    ) : null}
                  </DetailActions>
                }
              >
                {actionError ? <BtAlert tone="error">{actionError}</BtAlert> : null}

                <DetailSection title="Identity & scope">
                  <div className="bt-form-grid">
                    <FormField label="Name" className="sm:col-span-2">
                      <input className="bt-input" value={name} onChange={(e) => setName(e.target.value)} />
                    </FormField>
                    <FormField label="Borrower type">
                      <select className="bt-input" value={borrowerType} onChange={(e) => setBorrowerType(e.target.value as BorrowerType)}>
                        {BORROWER_TYPES.map((b) => (
                          <option key={b} value={b}>
                            {BORROWER_TYPE_LABELS[b]}
                          </option>
                        ))}
                      </select>
                    </FormField>
                    <FormField label="Loan product">
                      <select className="bt-input" value={loanProduct} onChange={(e) => setLoanProduct(e.target.value)}>
                        {LOAN_PRODUCT_CODES.map((c) => (
                          <option key={c} value={c}>
                            {LOAN_PRODUCT_LABELS[c]}
                          </option>
                        ))}
                        {loanProduct && !isLoanProductCode(loanProduct) ? (
                          <option value={loanProduct}>{loanProductLabel(loanProduct)} (legacy)</option>
                        ) : null}
                      </select>
                    </FormField>
                    <FormField label="Version">
                      <input type="number" className="bt-input" value={version} onChange={(e) => setVersion(Number(e.target.value) || 1)} />
                    </FormField>
                    <FormField label="Priority" hint="Higher priority scorecards are evaluated first">
                      <input type="number" className="bt-input" value={priority} onChange={(e) => setPriority(Number(e.target.value) || 0)} />
                    </FormField>
                    <FormField label="Min amount">
                      <input className="bt-input" value={minAmount} onChange={(e) => setMinAmount(e.target.value)} placeholder="Optional" />
                    </FormField>
                    <FormField label="Max amount">
                      <input className="bt-input" value={maxAmount} onChange={(e) => setMaxAmount(e.target.value)} placeholder="Optional" />
                    </FormField>
                    <FormField label="Geography (optional)" className="sm:col-span-2">
                      <div className="flex flex-wrap gap-2">
                        <input className="bt-input flex-1" placeholder="State" value={geoState} onChange={(e) => setGeoState(e.target.value)} />
                        <input className="bt-input flex-1" placeholder="City" value={geoCity} onChange={(e) => setGeoCity(e.target.value)} />
                      </div>
                    </FormField>
                    <label className="bt-checkbox-row sm:col-span-2">
                      <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                      Active — eligible for matching applications
                    </label>
                  </div>
                </DetailSection>

                <DetailSection title="Decision thresholds" description="Normalized score as % of maximum points">
                  <div className="flex flex-wrap gap-4">
                    <FormField label="Auto-approve at ≥ (%)">
                      <input type="number" className="bt-input w-24" value={approveMin} onChange={(e) => setApproveMin(Number(e.target.value) || 0)} />
                    </FormField>
                    <FormField label="Manual review at ≥ (%)">
                      <input type="number" className="bt-input w-24" value={manualMin} onChange={(e) => setManualMin(Number(e.target.value) || 0)} />
                    </FormField>
                  </div>
                </DetailSection>

                <DetailSection
                  title="Parameters"
                  description="Select source first — only parameters for that source are listed. OTHER allows custom parameters collected during underwriting."
                >
                  <div className="mb-2 flex justify-end">
                    <button type="button" className="bt-btn bt-btn-ghost bt-btn-sm" onClick={() => setGrid((g) => [...g, newRow()])}>
                      + Add parameter
                    </button>
                  </div>
                  <ScorecardParameterEditor rows={grid} onChange={setGrid} />
                </DetailSection>

                <DetailSection title="Hard rules" description="Evaluated before scoring — can force reject or manual review">
                  <div className="mb-2 flex justify-end">
                    <button type="button" className="bt-btn bt-btn-ghost bt-btn-sm" onClick={() => setHards((h) => [...h, newHard()])}>
                      + Add hard rule
                    </button>
                  </div>
                  <div className="space-y-3">
                    {hards.map((h, i) => {
                      const pDef = paramDef(h.source, h.parameter)
                      return (
                        <div key={h.id} className="bt-detail-subcard flex flex-wrap items-start gap-2 p-3">
                          <select
                            className="bt-input bt-input-sm"
                            value={h.source}
                            onChange={(e) => {
                              const source = e.target.value
                              const parameter = defaultParameterForSource(source)
                              setHards((a) =>
                                a.map((x, j) =>
                                  j === i
                                    ? { ...x, source, parameter, condition: defaultConditionForParam(paramDef(source, parameter)) }
                                    : x,
                                ),
                              )
                            }}
                          >
                            {SCORECARD_SOURCE_OPTIONS.map((s) => (
                              <option key={s.value} value={s.value}>
                                {s.label}
                              </option>
                            ))}
                          </select>
                          <select
                            className="bt-input bt-input-sm min-w-[10rem]"
                            value={h.parameter}
                            onChange={(e) => {
                              const parameter = e.target.value
                              setHards((a) =>
                                a.map((x, j) =>
                                  j === i
                                    ? { ...x, parameter, condition: defaultConditionForParam(paramDef(h.source, parameter)) }
                                    : x,
                                ),
                              )
                            }}
                          >
                            {parametersForSource(h.source).map((p) => (
                              <option key={p.value} value={p.value}>
                                {p.label}
                              </option>
                            ))}
                          </select>
                          <ScorecardConditionEditor
                            value={h.condition}
                            onChange={(condition) => setHards((a) => a.map((x, j) => (j === i ? { ...x, condition } : x)))}
                            paramDef={pDef}
                          />
                          <select
                            className="bt-input bt-input-sm"
                            value={h.decision}
                            onChange={(e) =>
                              setHards((a) =>
                                a.map((x, j) => (j === i ? { ...x, decision: e.target.value as HardRuleRow['decision'] } : x)),
                              )
                            }
                          >
                            <option value="REJECT">Reject</option>
                            <option value="MANUAL_REVIEW">Manual review</option>
                          </select>
                          <input
                            className="bt-input bt-input-sm min-w-[10rem] flex-1"
                            value={h.message ?? ''}
                            onChange={(e) => setHards((a) => a.map((x, j) => (j === i ? { ...x, message: e.target.value } : x)))}
                            placeholder="Optional message"
                          />
                          <button type="button" className="bt-btn-icon text-rose-600" onClick={() => setHards((a) => a.filter((_, j) => j !== i))}>
                            ×
                          </button>
                        </div>
                      )
                    })}
                    {hards.length === 0 ? <p className="text-sm text-slate-500">No hard rules configured.</p> : null}
                  </div>
                </DetailSection>
              </DetailPanel>
            ) : (
              <DetailEmptyState
                title="Select a scorecard"
                description="Choose a scorecard from the list to view and edit it, or create a new one."
                action={
                  <button type="button" onClick={startNew} className="bt-btn bt-btn-primary">
                    New scorecard
                  </button>
                }
              />
            )}
          </div>
        </MasterDetailLayout>
      )}
    </div>
  )
}
