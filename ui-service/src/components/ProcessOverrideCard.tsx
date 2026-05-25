import { useEffect, useState } from 'react'
import { applyManualOverride, getManualOverrideEligibility } from '@/api/flow'
import { messageForKycAction } from '@/api/kycErrorMessage'

export function ProcessOverrideCard({
  applicationId,
  processCode,
  failureCode,
  title = 'Manual override',
  onSuccess,
}: {
  applicationId: string
  processCode: string
  failureCode: string
  title?: string
  onSuccess: () => void
}) {
  const [eligibility, setEligibility] = useState<Record<string, unknown> | null>(null)
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [reason, setReason] = useState('')
  const [remarks, setRemarks] = useState('')
  const [approvalReference, setApprovalReference] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reload eligibility on process/failure changes
    setLoading(true)
    // eslint-disable-next-line react-hooks/set-state-in-effect -- clear stale messages before fetch
    setError(null)
    // eslint-disable-next-line react-hooks/set-state-in-effect -- clear stale messages before fetch
    setInfo(null)
    void getManualOverrideEligibility(applicationId, processCode, failureCode)
      .then((x) => {
        if (!cancelled) setEligibility(x)
      })
      .catch((e) => {
        if (!cancelled) setError(messageForKycAction(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [applicationId, processCode, failureCode])

  const canOverride = Boolean(eligibility?.canOverride)
  const requiresRemarks = Boolean(eligibility?.requiresRemarks)
  const requiresApproval = Boolean(eligibility?.requiresApproval)
  const reasonText = String(eligibility?.reason ?? '')

  async function onOverride() {
    setError(null)
    setInfo(null)
    if (reason.trim().length < 3) {
      setError('Please provide a clear override reason.')
      return
    }
    if (requiresRemarks && remarks.trim().length === 0) {
      setError('Remarks are required for this override.')
      return
    }
    if (requiresApproval && approvalReference.trim().length === 0) {
      setError('Approval reference is required for this override.')
      return
    }
    setBusy(true)
    try {
      await applyManualOverride(applicationId, {
        processCode,
        failureCode,
        overrideReason: reason.trim(),
        remarks: remarks.trim() || undefined,
        approvalReference: approvalReference.trim() || undefined,
      })
      setInfo('Manual override applied. Workflow can proceed from the overridden state.')
      setReason('')
      setRemarks('')
      setApprovalReference('')
      onSuccess()
    } catch (e) {
      setError(messageForKycAction(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
      <div className="mb-1 flex flex-wrap items-center gap-2">
        <span className="font-semibold">{title}</span>
        <span className="rounded bg-amber-100 px-2 py-0.5 text-xs">Controlled override</span>
      </div>
      {loading ? <p className="text-xs">Checking override eligibility…</p> : null}
      {!loading && !canOverride ? (
        <p className="text-xs">
          Override unavailable{reasonText ? `: ${reasonText}` : '.'}
        </p>
      ) : null}
      {canOverride ? (
        <div className="space-y-2">
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={2}
            className="w-full rounded border border-amber-200 bg-white px-2 py-1 text-sm text-slate-900"
            placeholder="Override reason (mandatory)"
          />
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={2}
            className="w-full rounded border border-amber-200 bg-white px-2 py-1 text-sm text-slate-900"
            placeholder={`Remarks${requiresRemarks ? ' (mandatory)' : ' (optional)'}`}
          />
          <input
            value={approvalReference}
            onChange={(e) => setApprovalReference(e.target.value)}
            className="w-full rounded border border-amber-200 bg-white px-2 py-1 text-sm text-slate-900"
            placeholder={`Approval reference${requiresApproval ? ' (mandatory)' : ' (optional)'}`}
          />
          <button
            type="button"
            onClick={() => void onOverride()}
            disabled={busy}
            className="rounded-md bg-amber-700 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-60"
          >
            {busy ? 'Applying override…' : 'Override failure'}
          </button>
        </div>
      ) : null}
      {error ? <p className="mt-2 text-xs text-red-700">{error}</p> : null}
      {info ? <p className="mt-2 text-xs text-emerald-700">{info}</p> : null}
    </div>
  )
}
