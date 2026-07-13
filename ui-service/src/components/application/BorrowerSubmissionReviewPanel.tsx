import { useState } from 'react'
import {
  acceptBorrowerSubmission,
  handOffToCreditOfficer,
  sendBackBorrowerSubmission,
  sendBackToRelationshipManager,
} from '@/api/workflow'
import { ApiError } from '@/api/http'
import { useAuth } from '@/auth/useAuth'
import {
  canAcceptBorrowerSubmission,
  canHandOffToCo,
  canSendBackToBorrower,
  canSendBackToRm,
} from '@/auth/types'
import { isInvoiceDiscountingBorrowerApp } from '@/lib/invoiceDiscountingFlow'
import { InvoiceDiscountingVintagePanel } from '@/components/plp/InvoiceDiscountingVintagePanel'
import type { ApplicationResponse } from '@/types/application'

function reviewNotesFromApp(app: ApplicationResponse): string | null {
  const fi = app.financialInfo as Record<string, unknown> | null
  const rn = fi?.reviewNotes
  if (!rn || typeof rn !== 'object') return null
  const notes = (rn as Record<string, unknown>).notes
  return typeof notes === 'string' && notes.trim() ? notes.trim() : null
}

export function BorrowerSubmissionReviewPanel({
  app,
  onRefetch,
}: {
  app: ApplicationResponse
  onRefetch: () => void | Promise<unknown>
}) {
  const { user } = useAuth()
  const role = user?.role ?? ''
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const status = app.status
  const showForRm =
    (status === 'BORROWER_SUBMITTED' || status === 'SENT_BACK_TO_RM') && canHandOffToCo(role)
  const showForCo = status === 'PENDING_CREDIT_OFFICER' && (canAcceptBorrowerSubmission(role) || canSendBackToRm(role))
  const showAdminAcceptFromSubmitted =
    status === 'BORROWER_SUBMITTED' &&
    (role === 'ADMIN' || role === 'ADMINISTRATOR') &&
    canAcceptBorrowerSubmission(role)

  if (!showForRm && !showForCo && !showAdminAcceptFromSubmitted) {
    return null
  }

  const priorNotes = reviewNotesFromApp(app)

  async function run(fn: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await fn()
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Action failed')
    } finally {
      setBusy(false)
    }
  }

  const headline =
    status === 'PENDING_CREDIT_OFFICER'
      ? 'Pending Credit Officer review'
      : status === 'SENT_BACK_TO_RM'
        ? 'Sent back to Relationship Manager'
        : 'Borrower submitted — review required'

  const blurb =
    status === 'PENDING_CREDIT_OFFICER'
      ? 'Accept to start KYC, or send back to the Relationship Manager with optional notes.'
      : status === 'SENT_BACK_TO_RM'
        ? 'Credit Officer returned this file. Hand off again to Credit Officer when ready.'
        : 'The borrower completed the delegated intake. Hand off to Credit Officer, or send back to the borrower.'

  return (
    <div className="bt-section-card mb-4 border-amber-200 bg-amber-50">
      <h3 className="bt-card-title mb-2">{headline}</h3>
      <p className="text-sm text-slate-700 mb-3">{blurb}</p>
      {priorNotes ? (
        <p className="text-sm text-amber-900 bg-amber-100/80 rounded p-2 mb-3 whitespace-pre-wrap">
          <span className="font-medium">Previous notes: </span>
          {priorNotes}
        </p>
      ) : null}
      {isInvoiceDiscountingBorrowerApp(app) ? (
        <div className="mb-4">
          <InvoiceDiscountingVintagePanel applicationId={app.id} />
        </div>
      ) : null}
      {error ? <p className="text-sm text-red-600 mb-2">{error}</p> : null}
      <textarea
        className="bt-input w-full mb-2"
        rows={3}
        placeholder="Optional notes"
        value={notes}
        onChange={(e) => setNotes(e.target.value)}
      />
      <div className="flex flex-wrap gap-2">
        {showForRm ? (
          <>
            <button
              type="button"
              className="bt-btn bt-btn--primary"
              disabled={busy}
              onClick={() => void run(() => handOffToCreditOfficer(app.id, notes))}
            >
              Hand off to Credit Officer
            </button>
            {status === 'BORROWER_SUBMITTED' && canSendBackToBorrower(role) ? (
              <button
                type="button"
                className="bt-btn bt-btn--secondary"
                disabled={busy}
                onClick={() => void run(() => sendBackBorrowerSubmission(app.id, notes))}
              >
                Send back to borrower
              </button>
            ) : null}
          </>
        ) : null}
        {showForCo ? (
          <>
            {canAcceptBorrowerSubmission(role) ? (
              <button
                type="button"
                className="bt-btn bt-btn--primary"
                disabled={busy}
                onClick={() => void run(() => acceptBorrowerSubmission(app.id))}
              >
                Accept for processing
              </button>
            ) : null}
            {canSendBackToRm(role) ? (
              <button
                type="button"
                className="bt-btn bt-btn--secondary"
                disabled={busy}
                onClick={() => void run(() => sendBackToRelationshipManager(app.id, notes))}
              >
                Send back to RM
              </button>
            ) : null}
          </>
        ) : null}
        {showAdminAcceptFromSubmitted ? (
          <button
            type="button"
            className="bt-btn bt-btn--primary"
            disabled={busy}
            onClick={() => void run(() => acceptBorrowerSubmission(app.id))}
          >
            Accept for processing
          </button>
        ) : null}
      </div>
    </div>
  )
}
