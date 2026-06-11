import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { downloadKfsPdfBlob } from '@/api/kfsApi'
import { getBorrowerApplicationDetail, getBorrowerKfsSummary } from '@/api/borrowerPortal'
import { listEsignRequests } from '@/api/esignRequests'
import { ApiError } from '@/api/http'
import { isUuid } from '@/lib/format'

type Tab = 'overview' | 'kfs' | 'loan'

export function BorrowerApplicationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [tab, setTab] = useState<Tab>('overview')
  const [data, setData] = useState<Awaited<ReturnType<typeof getBorrowerApplicationDetail>> | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [kfs, setKfs] = useState<Awaited<ReturnType<typeof getBorrowerKfsSummary>> | null>(null)
  const [kfsErr, setKfsErr] = useState<string | null>(null)
  const [esign, setEsign] = useState<Awaited<ReturnType<typeof listEsignRequests>>>([])

  useEffect(() => {
    if (!id || !isUuid(id)) return
    let c = true
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async load detail
    setErr(null)
    void getBorrowerApplicationDetail(id)
      .then((d) => {
        if (c) setData(d)
      })
      .catch((e) => {
        if (c) setErr(e instanceof ApiError ? e.message : 'Could not load application')
      })
    return () => {
      c = false
    }
  }, [id])

  useEffect(() => {
    if (tab !== 'kfs' || !id || !isUuid(id)) return
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async load KFS + eSign
    setKfsErr(null)
    void getBorrowerKfsSummary(id)
      .then((k) => {
        setKfs(k)
        return listEsignRequests(id)
      })
      .then(setEsign)
      .catch((e) => {
        setKfs(null)
        setKfsErr(e instanceof ApiError ? e.message : 'KFS is not available yet')
      })
  }, [tab, id])

  if (!id || !isUuid(id)) {
    return <p className="text-sm text-rose-700">Invalid application link.</p>
  }
  if (err) {
    return <p className="text-sm text-rose-700">{err}</p>
  }
  if (!data) {
    return <p className="text-sm text-slate-600">Loading…</p>
  }

  const disbursed = data.status === 'DISBURSED'
  const showLoan = disbursed

  const tabClass = (active: boolean) =>
    active
      ? '-mb-px border-b-2 border-bl-primary px-1 pb-3 font-semibold text-bl-navy'
      : '-mb-px border-b-2 border-transparent px-1 pb-3 text-slate-500 hover:text-slate-800'

  return (
    <div className="space-y-6">
      <Link
        to="/borrower/applications"
        className="inline-block text-sm font-medium text-slate-600 underline-offset-2 hover:text-slate-900 hover:underline"
      >
        ← All applications
      </Link>

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
        <h1 className="text-2xl font-semibold tracking-tight text-bl-navy">{data.friendlyStatusHeadline}</h1>
        <p className="mt-2 text-sm text-slate-600">
          {data.applicationNumber} · {data.product}
        </p>
        <p className="mt-4 text-sm leading-relaxed text-slate-800">{data.currentStageMessage}</p>
        {data.estimatedProcessingHint ? (
          <p className="mt-2 text-xs text-slate-500">{data.estimatedProcessingHint}</p>
        ) : null}
      </div>

      <nav className="border-b border-slate-200 text-sm" aria-label="Application sections">
        <div className="-mb-px flex flex-wrap gap-6 sm:gap-8">
          <button type="button" className={tabClass(tab === 'overview')} onClick={() => setTab('overview')}>
            Overview
          </button>
          <button type="button" className={tabClass(tab === 'kfs')} onClick={() => setTab('kfs')}>
            KFS &amp; agreement
          </button>
          {showLoan ? (
            <button type="button" className={tabClass(tab === 'loan')} onClick={() => setTab('loan')}>
              Disbursement &amp; loan
            </button>
          ) : null}
        </div>
      </nav>

      {tab === 'overview' ? (
        <div className="space-y-5">
          {data.rejectionMessage ? (
            <div className="rounded-lg border border-rose-200 bg-rose-50 p-5 text-sm text-rose-900 shadow-sm">
              <p className="font-medium">We could not proceed</p>
              <p className="mt-2 leading-relaxed">{data.rejectionMessage}</p>
              <div className="mt-4 flex flex-wrap gap-4">
                <a href="mailto:support@example.com" className="font-medium text-rose-950 underline">
                  Contact support
                </a>
                {data.reapplyVisible ? (
                  <Link to="/borrower/apply" className="font-medium text-rose-950 underline">
                    Re-apply
                  </Link>
                ) : null}
              </div>
            </div>
          ) : null}
          {data.collateralSummary && data.collateralSummary.length > 0 ? (
            <div className="rounded-lg border border-slate-200 bg-slate-50/80 p-5 text-sm text-slate-800 shadow-sm">
              <h2 className="font-medium text-slate-900">Security / collateral (your submission)</h2>
              <p className="mt-2 text-xs leading-relaxed text-slate-600">
                What you declared for this secured loan. Official valuation and approval follow our usual checks.
              </p>
              <ul className="mt-4 space-y-2">
                {data.collateralSummary.map((l, i) => (
                  <li key={`${l.label}-${i}`} className="flex flex-wrap gap-x-2">
                    <span className="text-slate-500">{l.label}</span>
                    <span className="text-slate-900">{l.value}</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
            <h2 className="text-sm font-semibold text-slate-900">Application progress</h2>
            <ol className="mt-4 space-y-4">
              {data.timeline.map((s) => (
                <li
                  key={s.id}
                  className="flex gap-4 rounded-lg border border-slate-100 bg-slate-50/50 p-4 text-sm"
                >
                  <span
                    className={[
                      'mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-bold',
                      s.state === 'completed'
                        ? 'bg-emerald-100 text-emerald-900'
                        : s.state === 'in_progress'
                          ? 'bg-amber-100 text-amber-900'
                          : s.state === 'locked'
                            ? 'bg-slate-200 text-slate-500'
                            : 'bg-slate-100 text-slate-500',
                    ].join(' ')}
                  >
                    {s.state === 'completed' ? '✓' : s.state === 'in_progress' ? '…' : s.state === 'locked' ? '—' : '○'}
                  </span>
                  <div className="min-w-0 flex-1 space-y-1">
                    <div className="font-medium text-slate-900">{s.label}</div>
                    <div className="leading-relaxed text-slate-600">{s.description}</div>
                    {s.completedAt ? (
                      <div className="text-xs text-slate-500">Updated {new Date(s.completedAt).toLocaleString()}</div>
                    ) : null}
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </div>
      ) : null}

      {tab === 'kfs' ? (
        <div className="space-y-4">
          {kfsErr ? <p className="text-sm text-amber-800">{kfsErr}</p> : null}
          {kfs ? (
            <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm shadow-sm sm:p-6">
              <h2 className="font-medium text-slate-900">Key Fact Statement (summary)</h2>
              <ul className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                <li className="rounded-md border border-slate-100 bg-slate-50/80 px-4 py-3">
                  <span className="block text-xs text-slate-500">Sanctioned</span>
                  <span className="font-medium text-slate-900">₹{kfs.sanctionedAmount}</span>
                </li>
                <li className="rounded-md border border-slate-100 bg-slate-50/80 px-4 py-3">
                  <span className="block text-xs text-slate-500">Interest</span>
                  <span className="font-medium text-slate-900">{kfs.interestRate}%</span>
                </li>
                <li className="rounded-md border border-slate-100 bg-slate-50/80 px-4 py-3">
                  <span className="block text-xs text-slate-500">APR</span>
                  <span className="font-medium text-slate-900">{kfs.apr}%</span>
                </li>
                <li className="rounded-md border border-slate-100 bg-slate-50/80 px-4 py-3">
                  <span className="block text-xs text-slate-500">Tenure</span>
                  <span className="font-medium text-slate-900">{kfs.tenureMonths} mo</span>
                </li>
                <li className="rounded-md border border-slate-100 bg-slate-50/80 px-4 py-3">
                  <span className="block text-xs text-slate-500">EMI</span>
                  <span className="font-medium text-slate-900">₹{kfs.emiAmount}</span>
                </li>
                <li className="rounded-md border border-slate-100 bg-slate-50/80 px-4 py-3">
                  <span className="block text-xs text-slate-500">Total repayment</span>
                  <span className="font-medium text-slate-900">₹{kfs.totalRepayment}</span>
                </li>
              </ul>
              <p className="mt-4 text-xs text-slate-500">
                Cooling-off: {kfs.coolingOffComplete ? 'Complete' : 'In progress'}
              </p>
              <div className="mt-5 flex flex-wrap gap-3">
                <button
                  type="button"
                  className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50"
                  onClick={() => {
                    void (async () => {
                      if (!id) return
                      const b = await downloadKfsPdfBlob(id)
                      const a = document.createElement('a')
                      a.href = URL.createObjectURL(b)
                      a.download = `kfs-${id}.pdf`
                      a.click()
                    })()
                  }}
                >
                  Download KFS
                </button>
                {kfs.signPending ? (
                  <a
                    href={esign[0]?.signingUrl ?? '#'}
                    className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
                    {...(esign[0]?.signingUrl ? { target: '_blank', rel: 'noreferrer' } : { 'aria-disabled': true })}
                  >
                    Sign KFS
                  </a>
                ) : null}
              </div>
            </div>
          ) : null}
        </div>
      ) : null}

      {tab === 'loan' && showLoan ? (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-800 shadow-sm sm:p-6">
          <h2 className="font-medium text-slate-900">Disbursement</h2>
          <dl className="mt-4 grid gap-4 sm:grid-cols-2">
            <div className="rounded-md border border-slate-100 bg-slate-50/80 px-4 py-3">
              <dt className="text-xs text-slate-500">Amount</dt>
              <dd className="mt-1 font-medium text-slate-900">
                ₹{data.disbursedAmount != null ? data.disbursedAmount : '—'}
              </dd>
            </div>
            <div className="rounded-md border border-slate-100 bg-slate-50/80 px-4 py-3">
              <dt className="text-xs text-slate-500">Date</dt>
              <dd className="mt-1 font-medium text-slate-900">
                {data.disbursedAt ? new Date(data.disbursedAt).toLocaleString() : '—'}
              </dd>
            </div>
            <div className="rounded-md border border-slate-100 bg-slate-50/80 px-4 py-3">
              <dt className="text-xs text-slate-500">Account</dt>
              <dd className="mt-1 font-medium text-slate-900">{data.disbursementAccountMask}</dd>
            </div>
            <div className="rounded-md border border-slate-100 bg-slate-50/80 px-4 py-3">
              <dt className="text-xs text-slate-500">Loan reference</dt>
              <dd className="mt-1 font-medium text-slate-900">{data.loanAccountNumber}</dd>
            </div>
          </dl>
          <div className="mt-6 flex flex-wrap gap-3">
            <Link
              to={`/borrower/loans/${id}/account`}
              className="rounded-md bg-bl-primary px-4 py-2 text-sm font-medium text-white hover:brightness-110"
            >
              Loan account & repay
            </Link>
            <Link
              to={`/borrower/loans/${id}/repayment`}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50"
            >
              Repayment schedule
            </Link>
            <Link
              to={`/borrower/loans/${id}/statement`}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50"
            >
              Statement of account
            </Link>
            <Link
              to={`/borrower/loans/${id}/transactions`}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50"
            >
              Transactions
            </Link>
          </div>
        </div>
      ) : null}
    </div>
  )
}
