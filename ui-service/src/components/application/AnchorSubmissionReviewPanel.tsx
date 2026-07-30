import { useState } from 'react'
import type { ApplicationResponse } from '@/types/application'
import { useAuth } from '@/auth/useAuth'
import {
  acceptBorrowerSubmission,
  handOffToCreditOfficer,
  sendBackToAnchor,
} from '@/api/workflow'
import { ApiError } from '@/api/http'

function canHandOff(role: string | undefined) {
  const r = (role ?? '').toUpperCase()
  return r.includes('RELATIONSHIP') || r.includes('RM') || r.includes('ADMIN')
}

function isAdmin(role: string | undefined) {
  const r = (role ?? '').toUpperCase()
  return r.includes('ADMIN') || r.includes('PLATFORM')
}

export function AnchorSubmissionReviewPanel({
  app,
  onRefetch,
}: {
  app: ApplicationResponse
  onRefetch: () => void
}) {
  const { user } = useAuth()
  const role = user?.role
  const status = app.status
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (app.intakeSegment !== 'ANCHOR' || app.intakeOwner !== 'ANCHOR') {
    return null
  }

  const showHandOff =
    (status === 'ANCHOR_SUBMITTED' || status === 'SENT_BACK_TO_RM') && canHandOff(role)
  const showAccept =
    status === 'PENDING_CREDIT_OFFICER' ||
    (status === 'ANCHOR_SUBMITTED' && isAdmin(role))
  const showSendBack =
    (status === 'ANCHOR_SUBMITTED' ||
      status === 'SENT_BACK_TO_RM' ||
      status === 'PENDING_CREDIT_OFFICER') &&
    canHandOff(role)

  if (!showHandOff && !showAccept && !showSendBack) {
    return null
  }

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await action()
      onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Action failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="bt-card mb-6">
      <h2 className="bt-card-title">Anchor intake review</h2>
      <p className="mt-1 text-sm text-slate-600">
        {status === 'ANCHOR_SUBMITTED'
          ? 'The anchor completed portal intake. Hand off to Credit Officer, accept (Admin), or send back to the anchor.'
          : 'Review this delegated anchor application.'}
      </p>
      {app.anchorSentBackNotes ? (
        <p className="mt-2 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          Prior send-back notes: {app.anchorSentBackNotes}
        </p>
      ) : null}
      <label className="mt-3 block text-sm">
        <span className="text-xs font-medium uppercase text-slate-500">Notes</span>
        <textarea
          className="mt-1 w-full rounded border border-slate-200 px-3 py-2 text-sm"
          rows={3}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
        />
      </label>
      {error ? <p className="mt-2 text-sm text-red-600">{error}</p> : null}
      <div className="mt-4 flex flex-wrap gap-2">
        {showHandOff ? (
          <button
            type="button"
            className="bt-btn bt-btn-primary"
            disabled={busy}
            onClick={() => void run(() => handOffToCreditOfficer(app.id, notes))}
          >
            Hand off to Credit Officer
          </button>
        ) : null}
        {showAccept ? (
          <button
            type="button"
            className="bt-btn bt-btn-primary"
            disabled={busy}
            onClick={() => void run(() => acceptBorrowerSubmission(app.id))}
          >
            Accept for KYC
          </button>
        ) : null}
        {showSendBack ? (
          <button
            type="button"
            className="bt-btn bt-btn-secondary"
            disabled={busy}
            onClick={() => void run(() => sendBackToAnchor(app.id, notes))}
          >
            Send back to anchor
          </button>
        ) : null}
      </div>
    </section>
  )
}
