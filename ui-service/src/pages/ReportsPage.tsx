import { useCallback, useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { canAccessAdminConfigNav } from '@/auth/types'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { BtCard, BtCardHeader } from '@/components/ui/BtCard'
import { BtStatCard, statAccentAt } from '@/components/ui/BtStatCard'
import {
  downloadReportExport,
  fetchDashboardAnalytics,
  fetchMisReport,
  fetchOperationsReport,
  fetchRegulatoryReport,
  type DashboardAnalytics,
  type OperationsReport,
} from '@/api/reports'
import { ApiError } from '@/api/http'

type TabKey = 'portfolio' | 'operations' | 'mis' | 'regulatory' | 'exports'

const TABS: { key: TabKey; label: string; desc: string }[] = [
  { key: 'portfolio', label: 'Portfolio funnel', desc: 'Application counts, rates, and status mix' },
  { key: 'operations', label: 'Operations queues', desc: 'Handoff, KYC, underwriting, sanction & eSign' },
  { key: 'mis', label: 'MIS', desc: 'Management information summary' },
  { key: 'regulatory', label: 'Regulatory', desc: 'Compliance-oriented aggregates' },
  { key: 'exports', label: 'Exports', desc: 'Download CSV, PDF, or Excel for a date range' },
]

function CountTable({
  rows,
  columns,
}: {
  rows: Record<string, unknown>[]
  columns: { key: string; label: string; align?: 'left' | 'right' | 'center' }[]
}) {
  if (!rows.length) {
    return <p className="text-sm text-[var(--bt-gray-500)]">No rows for the selected filters.</p>
  }
  return (
    <div className="overflow-x-auto">
      <table className="bt-table w-full text-sm">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={c.align === 'right' ? 'text-right' : c.align === 'center' ? 'text-center' : 'text-left'}>
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i}>
              {columns.map((c) => (
                <td
                  key={c.key}
                  className={c.align === 'right' ? 'text-right' : c.align === 'center' ? 'text-center' : 'text-left'}
                >
                  {String(row[c.key] ?? '—')}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function ReportsPage() {
  const { user } = useAuth()
  const allowed = user ? canAccessAdminConfigNav(user.role) : false

  const [tab, setTab] = useState<TabKey>('portfolio')
  const [period, setPeriod] = useState('30D')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [dashboard, setDashboard] = useState<DashboardAnalytics | null>(null)
  const [operations, setOperations] = useState<OperationsReport | null>(null)
  const [mis, setMis] = useState<Record<string, unknown> | null>(null)
  const [regulatory, setRegulatory] = useState<Record<string, unknown> | null>(null)
  const [exportBusy, setExportBusy] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (tab === 'portfolio') {
        setDashboard(await fetchDashboardAnalytics())
      } else if (tab === 'operations') {
        setOperations(await fetchOperationsReport(period))
      } else if (tab === 'mis') {
        setMis(await fetchMisReport(period))
      } else if (tab === 'regulatory') {
        setRegulatory(await fetchRegulatoryReport(period))
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load report')
    } finally {
      setLoading(false)
    }
  }, [tab, period])

  useEffect(() => {
    if (!allowed) return
    if (tab === 'exports') {
      setLoading(false)
      return
    }
    void load()
  }, [allowed, tab, load])

  if (!allowed) {
    return <Navigate to="/dashboard" replace />
  }

  async function runExport(reportType: string, format: 'csv' | 'pdf' | 'xlsx') {
    setExportBusy(`${reportType}-${format}`)
    setError(null)
    try {
      await downloadReportExport(reportType, format, startDate || undefined, endDate || undefined)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Export failed')
    } finally {
      setExportBusy(null)
    }
  }

  const current = TABS.find((t) => t.key === tab)!

  return (
    <div>
      <PageHeader title="Reports" description="Admin analytics, operations queues, MIS, and file exports." />

      <div className="bt-toolbar-card mb-4 flex flex-wrap items-end gap-3">
        <div>
          <label className="bt-label">Section</label>
          <div className="flex flex-wrap gap-2 mt-1">
            {TABS.map((t) => (
              <button
                key={t.key}
                type="button"
                className={tab === t.key ? 'bt-btn bt-btn--primary' : 'bt-btn bt-btn--secondary'}
                onClick={() => setTab(t.key)}
              >
                {t.label}
              </button>
            ))}
          </div>
        </div>
        {tab !== 'portfolio' && tab !== 'exports' ? (
          <div>
            <label className="bt-label" htmlFor="rpt-period">
              Period
            </label>
            <select
              id="rpt-period"
              className="bt-input"
              value={period}
              onChange={(e) => setPeriod(e.target.value)}
            >
              <option value="7D">Last 7 days</option>
              <option value="30D">Last 30 days</option>
              <option value="90D">Last 90 days</option>
              <option value="YTD">Year to date</option>
              <option value="ALL">All time</option>
            </select>
          </div>
        ) : null}
        <button type="button" className="bt-btn bt-btn--secondary" disabled={loading || tab === 'exports'} onClick={() => void load()}>
          Refresh
        </button>
      </div>

      <p className="text-sm text-[var(--bt-gray-600)] mb-4">{current.desc}</p>
      {error ? <ErrorState message={error} /> : null}
      {loading && tab !== 'exports' ? <LoadingState label="Loading report…" /> : null}

      {!loading && tab === 'portfolio' && dashboard ? (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <BtStatCard title="Total applications" value={String(dashboard.totalApplications)} accent={statAccentAt(0)} />
            <BtStatCard title="Active pipeline" value={String(dashboard.activePipeline)} accent={statAccentAt(1)} />
            <BtStatCard title="Disbursed" value={String(dashboard.disbursedCount)} accent={statAccentAt(2)} />
            <BtStatCard
              title="Approval rate"
              value={`${(dashboard.approvalRate ?? 0).toFixed(1)}%`}
              accent={statAccentAt(3)}
            />
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <BtCard>
              <BtCardHeader title="Status distribution" />
              <CountTable
                rows={(dashboard.statusDistribution ?? []) as unknown as Record<string, unknown>[]}
                columns={[
                  { key: 'status', label: 'Status' },
                  { key: 'count', label: 'Count', align: 'right' },
                  { key: 'percentage', label: '%', align: 'right' },
                ]}
              />
            </BtCard>
            <BtCard>
              <BtCardHeader title="Product mix" />
              <CountTable
                rows={(dashboard.productDistribution ?? []) as unknown as Record<string, unknown>[]}
                columns={[
                  { key: 'product', label: 'Product' },
                  { key: 'count', label: 'Count', align: 'right' },
                ]}
              />
            </BtCard>
          </div>
        </div>
      ) : null}

      {!loading && tab === 'operations' && operations ? (
        <div className="space-y-6">
          <div className="grid gap-4 lg:grid-cols-2">
            <BtCard>
              <BtCardHeader title="RM ↔ CO handoff queue" />
              <CountTable
                rows={operations.handoffQueue as unknown as Record<string, unknown>[]}
                columns={[
                  { key: 'status', label: 'Status' },
                  { key: 'count', label: 'Count', align: 'right' },
                ]}
              />
            </BtCard>
            <BtCard>
              <BtCardHeader title="Intake segment" />
              <CountTable
                rows={operations.intakeSegmentCounts as unknown as Record<string, unknown>[]}
                columns={[
                  { key: 'segment', label: 'Segment' },
                  { key: 'count', label: 'Count', align: 'right' },
                ]}
              />
            </BtCard>
            <BtCard>
              <BtCardHeader title="KYC & underwriting" />
              <CountTable
                rows={operations.kycAndUnderwriting as unknown as Record<string, unknown>[]}
                columns={[
                  { key: 'status', label: 'Status' },
                  { key: 'count', label: 'Count', align: 'right' },
                ]}
              />
            </BtCard>
            <BtCard>
              <BtCardHeader title="Sanction & eSign pipeline" />
              <CountTable
                rows={operations.sanctionAndEsign as unknown as Record<string, unknown>[]}
                columns={[
                  { key: 'status', label: 'Status' },
                  { key: 'count', label: 'Count', align: 'right' },
                ]}
              />
            </BtCard>
          </div>
          <BtCard>
            <BtCardHeader title="Product breakdown" />
            <CountTable
              rows={operations.productCounts as unknown as Record<string, unknown>[]}
              columns={[
                { key: 'product', label: 'Product' },
                { key: 'count', label: 'Count', align: 'right' },
              ]}
            />
          </BtCard>
        </div>
      ) : null}

      {!loading && tab === 'mis' && mis ? (
        <BtCard>
          <BtCardHeader title="MIS snapshot" />
          <pre className="text-xs overflow-auto max-h-[480px] bg-[var(--bt-gray-50)] p-3 rounded">
            {JSON.stringify(mis, null, 2)}
          </pre>
        </BtCard>
      ) : null}

      {!loading && tab === 'regulatory' && regulatory ? (
        <BtCard>
          <BtCardHeader title="Regulatory snapshot" />
          <pre className="text-xs overflow-auto max-h-[480px] bg-[var(--bt-gray-50)] p-3 rounded">
            {JSON.stringify(regulatory, null, 2)}
          </pre>
        </BtCard>
      ) : null}

      {tab === 'exports' ? (
        <BtCard>
          <BtCardHeader title="Download reports" />
          <div className="flex flex-wrap gap-3 mb-4">
            <div>
              <label className="bt-label" htmlFor="export-start">
                Start date
              </label>
              <input
                id="export-start"
                type="date"
                className="bt-input"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
              />
            </div>
            <div>
              <label className="bt-label" htmlFor="export-end">
                End date
              </label>
              <input
                id="export-end"
                type="date"
                className="bt-input"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
              />
            </div>
          </div>
          <div className="space-y-3">
            {(['MIS', 'PORTFOLIO', 'DISBURSEMENT', 'REGULATORY', 'DSA_PERFORMANCE'] as const).map((rt) => (
              <div key={rt} className="flex flex-wrap items-center gap-2 border-b border-[var(--bt-gray-100)] pb-3">
                <span className="min-w-[10rem] font-medium text-sm">{rt.replace('_', ' ')}</span>
                {(['csv', 'xlsx', 'pdf'] as const).map((fmt) => (
                  <button
                    key={fmt}
                    type="button"
                    className="bt-btn bt-btn--secondary"
                    disabled={exportBusy === `${rt}-${fmt}`}
                    onClick={() => void runExport(rt, fmt)}
                  >
                    {exportBusy === `${rt}-${fmt}` ? '…' : fmt.toUpperCase()}
                  </button>
                ))}
              </div>
            ))}
          </div>
        </BtCard>
      ) : null}
    </div>
  )
}
