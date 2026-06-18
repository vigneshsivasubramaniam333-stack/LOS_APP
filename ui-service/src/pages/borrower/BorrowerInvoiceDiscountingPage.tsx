import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  acceptInvoice,
  getInvoiceDiscounting,
  repayInvoiceLoan,
  requestInvoiceFinance,
  type BorrowerInvoiceDiscounting,
  type BorrowerInvoiceItem,
  type BorrowerInvoiceLoan,
} from '@/api/borrowerInvoiceDiscounting'
import { getBorrowerDashboard } from '@/api/borrowerPortal'
import { BorrowerInvoiceLoanCard } from '@/components/borrower/BorrowerInvoiceLoanCard'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'

function money(n: number | null | undefined): string {
  if (n == null) return '—'
  return `₹${Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
}

function defaultFinanceAmount(inv: BorrowerInvoiceItem): string {
  const amt =
    inv.suggestedFinanceAmount ??
    inv.maxFinanceableAmount ??
    inv.availableAmount ??
    inv.eligibleAmount ??
    inv.invoiceAmount
  return amt != null && amt > 0 ? String(amt) : ''
}

function statusBadge(status: string | null): string {
  const s = (status ?? '').toUpperCase()
  if (s.includes('OVERDUE') || s === 'REJECTED') return 'bg-rose-50 text-rose-700 border-rose-200'
  if (s.includes('DISCOUNTED') || s === 'DISBURSED' || s === 'CLOSED')
    return 'bg-emerald-50 text-emerald-700 border-emerald-200'
  if (s === 'ELIGIBLE' || s === 'BORROWER_ACCEPTED') return 'bg-sky-50 text-sky-700 border-sky-200'
  return 'bg-slate-50 text-slate-600 border-slate-200'
}

export function BorrowerInvoiceDiscountingPage() {
  const [linked, setLinked] = useState<boolean | null>(null)
  const [data, setData] = useState<BorrowerInvoiceDiscounting | null>(null)
  const [loadErr, setLoadErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [actionErr, setActionErr] = useState<string | null>(null)
  const [actionOk, setActionOk] = useState<string | null>(null)
  const [financeAmounts, setFinanceAmounts] = useState<Record<string, string>>({})
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({})
  const [expandedLoanInvoiceIds, setExpandedLoanInvoiceIds] = useState<Set<string>>(() => new Set())

  const invoiceById = useMemo(() => {
    const map = new Map<string, BorrowerInvoiceItem>()
    for (const inv of data?.invoices ?? []) {
      map.set(inv.invoiceId, inv)
    }
    return map
  }, [data?.invoices])

  const loansByInvoiceId = useMemo(() => {
    const map = new Map<string, BorrowerInvoiceLoan[]>()
    for (const loan of data?.loans ?? []) {
      const invId = loan.invoiceId
      if (!invId) continue
      const list = map.get(invId) ?? []
      list.push(loan)
      map.set(invId, list)
    }
    return map
  }, [data?.loans])

  const toggleLoanDetails = (invoiceId: string) => {
    setExpandedLoanInvoiceIds((prev) => {
      const next = new Set(prev)
      if (next.has(invoiceId)) next.delete(invoiceId)
      else next.add(invoiceId)
      return next
    })
  }

  const applyFinanceDefaults = useCallback((payload: BorrowerInvoiceDiscounting) => {
    setFinanceAmounts((prev) => {
      const next = { ...prev }
      for (const inv of payload.invoices) {
        if (inv.financeable) {
          next[inv.invoiceId] = defaultFinanceAmount(inv)
        }
      }
      return next
    })
    setRepayAmounts((prev) => {
      const next = { ...prev }
      for (const loan of payload.loans) {
        if (loan.repayable && !next[loan.loanId]) {
          const out = loan.outstandingAmount
          if (out != null && out > 0) next[loan.loanId] = String(out)
        }
      }
      return next
    })
  }, [])

  const refresh = useCallback(async () => {
    setLoading(true)
    setLoadErr(null)
    try {
      const dash = await getBorrowerDashboard()
      if (!dash.invoiceDiscountingLinked) {
        setLinked(false)
        return
      }
      setLinked(true)
      const payload = await getInvoiceDiscounting()
      setData(payload)
      applyFinanceDefaults(payload)
    } catch (e) {
      setLoadErr(e instanceof ApiError ? e.message : 'Failed to load invoice discounting')
    } finally {
      setLoading(false)
    }
  }, [applyFinanceDefaults])

  useEffect(() => {
    void refresh()
  }, [refresh])

  async function onAccept(inv: BorrowerInvoiceItem) {
    setActionErr(null)
    setActionOk(null)
    setBusyId(inv.invoiceId)
    try {
      const updated = await acceptInvoice(inv.invoiceId)
      setData(updated)
      applyFinanceDefaults(updated)
      setActionOk(`Invoice ${inv.invoiceNumber ?? ''} accepted. You can now request finance.`)
    } catch (ex) {
      setActionErr(ex instanceof ApiError ? ex.message : 'Could not accept the invoice.')
    } finally {
      setBusyId(null)
    }
  }

  async function onFinance(e: FormEvent, inv: BorrowerInvoiceItem) {
    e.preventDefault()
    setActionErr(null)
    setActionOk(null)
    const value = Number(financeAmounts[inv.invoiceId] ?? '')
    if (!Number.isFinite(value) || value <= 0) {
      setActionErr('Enter a valid finance amount greater than 0.')
      return
    }
    setBusyId(inv.invoiceId)
    try {
      const updated = await requestInvoiceFinance(inv.invoiceId, value)
      setData(updated)
      applyFinanceDefaults(updated)
      setActionOk(`Finance request of ${money(value)} submitted for invoice ${inv.invoiceNumber ?? ''}.`)
    } catch (ex) {
      setActionErr(ex instanceof ApiError ? ex.message : 'Could not submit the finance request.')
    } finally {
      setBusyId(null)
    }
  }

  async function onRepay(e: FormEvent, loan: BorrowerInvoiceLoan) {
    e.preventDefault()
    setActionErr(null)
    setActionOk(null)
    const value = Number(repayAmounts[loan.loanId] ?? '')
    if (!Number.isFinite(value) || value <= 0) {
      setActionErr('Enter a valid repayment amount greater than 0.')
      return
    }
    setBusyId(loan.loanId)
    try {
      const updated = await repayInvoiceLoan(loan.loanId, value)
      setData(updated)
      applyFinanceDefaults(updated)
      setRepayAmounts((m) => ({ ...m, [loan.loanId]: '' }))
      setActionOk(`Repayment of ${money(value)} recorded for loan ${loan.loanNumber ?? ''}.`)
    } catch (ex) {
      setActionErr(ex instanceof ApiError ? ex.message : 'Could not record the repayment.')
    } finally {
      setBusyId(null)
    }
  }

  if (linked === false && !loading) {
    return <Navigate to="/borrower/dashboard" replace />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Invoice discounting"
        description="Accept eligible invoices, request finance, track repayments, and manage outstanding loans."
      />

      {loadErr ? <p className="text-sm text-amber-800">{loadErr}</p> : null}
      {loading ? <p className="text-sm text-slate-600">Loading…</p> : null}

      {data && !data.available ? (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-5 text-sm text-slate-600 shadow-sm">
          {data.message ?? 'Invoice discounting is not available for your account yet.'}
        </div>
      ) : null}

      {actionErr ? <p className="text-sm text-rose-700">{actionErr}</p> : null}
      {actionOk ? <p className="text-sm text-emerald-700">{actionOk}</p> : null}

      {data && data.available ? (
        <>
          <p className="text-sm text-slate-600">
            Purchase-flow invoices must be accepted before requesting finance.
          </p>

          <section className="space-y-4">
            <h2 className="text-sm font-semibold text-bl-navy">Your invoices</h2>
            {data.invoices.length === 0 ? (
              <p className="text-sm text-slate-500">No invoices found for your account yet.</p>
            ) : (
              <div className="bt-card overflow-x-auto shadow-sm">
                <table className="min-w-full text-sm">
                  <thead className="border-b border-slate-200 bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-600">
                    <tr>
                      <th className="px-5 py-3.5">Invoice</th>
                      <th className="px-5 py-3.5">Due date</th>
                      <th className="px-5 py-3.5">Amount</th>
                      <th className="px-5 py-3.5">Available</th>
                      <th className="px-5 py-3.5">Status</th>
                      <th className="px-5 py-3.5">Action</th>
                    </tr>
                  </thead>
                  <tbody className="">
                    {data.invoices.flatMap((inv) => {
                      const linkedLoans = loansByInvoiceId.get(inv.invoiceId) ?? []
                      const rows = [
                        <tr key={inv.invoiceId} className="align-middle ">
                        <td className="whitespace-nowrap px-5 py-4 font-medium text-bl-navy">{inv.invoiceNumber ?? '—'}</td>
                        <td className="whitespace-nowrap px-5 py-4 text-slate-600">{inv.dueDate ?? '—'}</td>
                        <td className="px-5 py-4 tabular-nums text-slate-700">{money(inv.invoiceAmount)}</td>
                        <td className="px-5 py-4 tabular-nums text-slate-700">{money(inv.availableAmount)}</td>
                        <td className="px-5 py-4">
                          <span
                            className={`inline-block rounded-full border px-2 py-0.5 text-xs ${statusBadge(inv.status)}`}
                          >
                            {inv.friendlyStatus || inv.status || '—'}
                          </span>
                        </td>
                        <td className="px-5 py-4">
                          <div className="flex flex-col items-start gap-2">
                            {linkedLoans.length > 0 ? (
                              <button
                                type="button"
                                onClick={() => toggleLoanDetails(inv.invoiceId)}
                                className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                                title={expandedLoanInvoiceIds.has(inv.invoiceId) ? 'Hide linked loan details' : 'View linked loan details'}
                              >
                                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="h-3.5 w-3.5" aria-hidden>
                                  {expandedLoanInvoiceIds.has(inv.invoiceId) ? (
                                    <path fillRule="evenodd" d="M3.28 2.22a.75.75 0 0 0-1.06 1.06l14.5 14.5a.75.75 0 1 0 1.06-1.06l-1.745-1.745a10.029 10.029 0 0 0 3.3-4.38 1.5 1.5 0 0 0 0-1.5 10.029 10.029 0 0 0-3.3-4.38 1.5 1.5 0 0 0-1.5 0 10.029 10.029 0 0 0-3.3 4.38 1.5 1.5 0 0 0 1.5 0 10.029 10.029 0 0 0 4.38 3.3l1.745 1.745a.75.75 0 0 0 1.06-1.06l-14.5-14.5Z" clipRule="evenodd" />
                                  ) : (
                                    <>
                                      <path d="M10 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z" />
                                      <path fillRule="evenodd" d="M.664 10.59a1.651 1.651 0 0 1 0-1.186A10.004 10.004 0 0 1 10 3c4.257 0 7.893 2.66 9.336 6.41.147.381.146.804 0 1.186A10.004 10.004 0 0 1 10 17c-4.257 0-7.893-2.66-9.336-6.41ZM14 10a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z" clipRule="evenodd" />
                                    </>
                                  )}
                                </svg>
                                {expandedLoanInvoiceIds.has(inv.invoiceId) ? 'Hide loan' : 'View loan'}
                              </button>
                            ) : null}
                            {inv.acceptable ? (
                              <button
                                type="button"
                                onClick={() => void onAccept(inv)}
                                disabled={busyId === inv.invoiceId}
                                className="rounded-md border border-sky-600 px-3 py-1.5 text-xs font-medium text-sky-700 hover:bg-sky-50 disabled:opacity-50"
                              >
                                {busyId === inv.invoiceId ? '…' : 'Accept invoice'}
                              </button>
                            ) : inv.financeable ? (
                              <form onSubmit={(e) => onFinance(e, inv)} className="flex flex-wrap items-center gap-3">
                                <input
                                  type="number"
                                  min="1"
                                  step="0.01"
                                  value={financeAmounts[inv.invoiceId] ?? defaultFinanceAmount(inv)}
                                  onChange={(e) =>
                                    setFinanceAmounts((m) => ({ ...m, [inv.invoiceId]: e.target.value }))
                                  }
                                  className="w-28 rounded border border-slate-300 px-2 py-1 text-sm focus:border-bl-primary focus:outline-none focus:ring-1 focus:ring-bl-primary/30"
                                />
                                <button
                                  type="submit"
                                  disabled={busyId === inv.invoiceId}
                                  className="bt-btn bt-btn-primary bt-btn-sm disabled:opacity-50"
                                >
                                  {busyId === inv.invoiceId ? '…' : 'Request finance'}
                                </button>
                              </form>
                            ) : linkedLoans.length === 0 ? (
                              <span className="text-xs text-slate-400">Not available</span>
                            ) : null}
                          </div>
                        </td>
                      </tr>,
                      ]
                      if (linkedLoans.length > 0 && expandedLoanInvoiceIds.has(inv.invoiceId)) {
                        rows.push(
                          <tr key={`${inv.invoiceId}-loans`}>
                            <td colSpan={6} className="px-5 pb-4 bg-slate-50/40">
                              <div className="space-y-4">
                                {linkedLoans.map((loan) => (
                                  <BorrowerInvoiceLoanCard
                                    key={loan.loanId}
                                    loan={loan}
                                    invoice={invoiceById.get(inv.invoiceId)}
                                    busyId={busyId}
                                    repayAmount={repayAmounts[loan.loanId] ?? ''}
                                    onRepayAmountChange={(value) =>
                                      setRepayAmounts((m) => ({ ...m, [loan.loanId]: value }))
                                    }
                                    onRepay={onRepay}
                                  />
                                ))}
                              </div>
                            </td>
                          </tr>,
                        )
                      }
                      return rows
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      ) : null}
    </div>
  )
}
