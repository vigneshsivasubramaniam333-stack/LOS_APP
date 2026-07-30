import { useState } from 'react'
import type { ApplicationResponse } from '@/types/application'
import { useAuth } from '@/auth/useAuth'
import { approveDocumentVerification, sendBackDocumentVerification } from '@/api/workflow'
import { ApiError } from '@/api/http'

function canOps(role: string | undefined) {
  const r = (role ?? '').toUpperCase()
  return r.includes('OPERATIONS') || r.includes('ADMIN') || r.includes('PLATFORM')
}

export function AnchorDocVerificationPanel({
  app,
  onRefetch,
}: {
  app: ApplicationResponse
  onRefetch: () => void
}) {
  const { user } = useAuth()
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (app.intakeSegment !== 'ANCHOR') return null
  if (
    app.status !== 'DOC_VERIFICATION_PENDING' &&
    app.status !== 'DOC_VERIFICATION_SENT_BACK'
  ) {
    return null
  }
  if (!canOps(user?.role)) return null

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
      <h2 className="bt-card-title">Document verification</h2>
      <p className="mt-1 text-sm text-slate-600">
        eSign is complete. Review signed program terms and supporting documents, then approve to activate the
        program or send back to the anchor for corrections.
      </p>
      {app.docVerificationNotes ? (
        <p className="mt-2 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          Notes: {app.docVerificationNotes}
        </p>
      ) : null}
      <label className="mt-3 block text-sm">
        <span className="text-xs font-medium uppercase text-slate-500">Send-back notes</span>
        <textarea
          className="mt-1 w-full rounded border border-slate-200 px-3 py-2 text-sm"
          rows={3}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
        />
      </label>
      {error ? <p className="mt-2 text-sm text-red-600">{error}</p> : null}
      <div className="mt-4 flex flex-wrap gap-2">
        <button
          type="button"
          className="bt-btn bt-btn-primary"
          disabled={busy || app.status === 'DOC_VERIFICATION_SENT_BACK'}
          onClick={() => void run(() => approveDocumentVerification(app.id))}
        >
          Approve &amp; activate program
        </button>
        {app.status === 'DOC_VERIFICATION_PENDING' ? (
          <button
            type="button"
            className="bt-btn bt-btn-secondary"
            disabled={busy}
            onClick={() => void run(() => sendBackDocumentVerification(app.id, notes))}
          >
            Send back to anchor
          </button>
        ) : null}
      </div>
    </section>
  )
}
