import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApplicationTable } from '@/components/ApplicationTable'
import { ClearDemoDataButton } from '@/components/ClearDemoDataButton'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { useApplications } from '@/hooks/useApplications'
import type { ApplicationStatus } from '@/types/application'

const STATUS_OPTIONS: (ApplicationStatus | '')[] = [
  '',
  'DRAFT',
  'CONSENT_PENDING',
  'KYC_IN_PROGRESS',
  'KYC_FAILED',
  'UNDERWRITING',
  'APPROVED',
  'REJECTED',
  'SANCTION_ISSUED',
  'ESIGN_PENDING',
  'ESIGN_COMPLETED',
  'DISBURSEMENT_PENDING',
  'DISBURSED',
  'WITHDRAWN',
  'ON_HOLD',
]

const INTAKE_OPTIONS: { value: '' | 'BORROWER' | 'ANCHOR'; label: string }[] = [
  { value: '', label: 'All intakes' },
  { value: 'BORROWER', label: 'Borrower only' },
  { value: 'ANCHOR', label: 'Anchor only' },
]

function parseStatus(s: string | null): ApplicationStatus | undefined {
  if (!s) return undefined
  if (STATUS_OPTIONS.includes(s as ApplicationStatus | '')) {
    if (s === '') return undefined
    return s as ApplicationStatus
  }
  return undefined
}

function parseIntakeSegment(s: string | null): string | undefined {
  if (!s) return undefined
  if (s === 'BORROWER' || s === 'ANCHOR') return s
  return undefined
}

export function ApplicationsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = useMemo(
    () => parseStatus(searchParams.get('status')),
    [searchParams],
  )
  const intakeSegment = useMemo(
    () => parseIntakeSegment(searchParams.get('intakeSegment')),
    [searchParams],
  )
  const { data, loading, error, refetch } = useApplications({ status, intakeSegment, page: 0, size: 30 })

  function setParam(key: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    setSearchParams(next)
  }

  return (
    <div>
      <PageHeader
        title="Applications"
        description="Browse the loan application queue. Filter by processing status to find what to work on next."
      />
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-3">
          <label className="block text-sm text-slate-600">
            <span className="mb-1 block text-xs font-medium text-slate-500">Status filter</span>
            <select
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-900"
              value={searchParams.get('status') ?? ''}
              onChange={(e) => setParam('status', e.target.value)}
            >
              {STATUS_OPTIONS.map((s) => (
                <option key={s || 'ALL'} value={s}>
                  {s || 'All'}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm text-slate-600">
            <span className="mb-1 block text-xs font-medium text-slate-500">Intake</span>
            <select
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-900"
              value={searchParams.get('intakeSegment') ?? ''}
              onChange={(e) => setParam('intakeSegment', e.target.value)}
            >
              {INTAKE_OPTIONS.map((o) => (
                <option key={o.value || 'ALL'} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <span className="text-sm text-slate-500">
            {data != null
              ? `${data.numberOfElements} of ${data.totalElements} (page ${data.number + 1} / ${Math.max(1, data.totalPages)})`
              : null}
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ClearDemoDataButton onCleared={refetch} />
          <Link
            to="/applications/new"
            className="shrink-0 rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-800"
          >
            New application
          </Link>
        </div>
      </div>
      {loading && <LoadingState label="Loading applications…" />}
      {error && <ErrorState message={error} />}
      {data && !loading && <ApplicationTable rows={data.content} />}
    </div>
  )
}
