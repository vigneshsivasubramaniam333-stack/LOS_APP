import { useEffect, useState } from 'react'
import {
  getProgramApproval,
  refreshPlpProgramStatus,
  type ProgramApprovalResponse,
  type ProgramApprovalStatus,
} from '@/api/workflow'
import { ApiError } from '@/api/http'

const STATUS_LABEL: Record<ProgramApprovalStatus, string> = {
  DRAFT: 'Draft — awaiting PLP L1',
  PENDING_L2: 'Pending L2 in PLP',
  SENT_BACK: 'Sent back to RM',
  APPROVED: 'Approved',
  REJECTED: 'Rejected / not active',
}

/**
 * Read-only mirror of PLP program approval. L1/L2 actions happen in PLP;
 * use Refresh after PLP send-back or approve.
 */
export function ProgramApprovalPanel({
  programId,
  onUpdated,
}: {
  programId: string
  onUpdated?: () => void
}) {
  const [approval, setApproval] = useState<ProgramApprovalResponse | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  async function load() {
    try {
      setApproval(await getProgramApproval(programId))
    } catch {
      setApproval(null)
    }
  }

  useEffect(() => {
    void (async () => {
      setLoading(true)
      await load()
      setLoading(false)
    })()
  }, [programId])

  async function onRefresh() {
    setBusy(true)
    setError(null)
    try {
      setApproval(await refreshPlpProgramStatus(programId))
      onUpdated?.()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to refresh from PLP')
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return (
      <div className="mt-4 rounded border border-slate-200 bg-white p-4 text-sm text-slate-600">
        Loading approval status…
      </div>
    )
  }

  if (!approval) {
    return (
      <div className="mt-4 rounded border border-slate-200 bg-white p-4">
        <button type="button" className="bt-btn bt-btn--secondary" onClick={() => void load()}>
          Load approval status
        </button>
      </div>
    )
  }

  const st = approval.approvalStatus
  return (
    <div className="mt-4 rounded border border-slate-200 bg-white p-4">
      <h4 className="bt-card-title mb-2">Program approval (mirrored from PLP)</h4>
      <p className="text-sm text-slate-600 mb-2">
        Status: <strong>{STATUS_LABEL[st]}</strong>
        {approval.plpOperationalStatus ? (
          <>
            {' '}
            · PLP: <strong>{approval.plpOperationalStatus}</strong>
          </>
        ) : null}
      </p>
      {st === 'SENT_BACK' ? (
        <div className="text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded p-3 mb-2">
          <p className="font-medium mb-1">Sent back — reason for RM</p>
          <p className="whitespace-pre-wrap">
            {approval.approvalNotes?.trim()
              ? approval.approvalNotes
              : 'No send-back reason was provided from PLP.'}
          </p>
        </div>
      ) : approval.approvalNotes ? (
        <p className="text-sm text-amber-800 bg-amber-50 rounded p-2 mb-2 whitespace-pre-wrap">
          {approval.approvalNotes}
        </p>
      ) : null}
      {error ? <p className="text-sm text-red-600 mb-2">{error}</p> : null}
      <div className="flex justify-end">
        <button
          type="button"
          disabled={busy}
          className="bt-btn bt-btn--secondary"
          onClick={() => void onRefresh()}
        >
          {busy ? 'Refreshing…' : 'Refresh from PLP'}
        </button>
      </div>
    </div>
  )
}
