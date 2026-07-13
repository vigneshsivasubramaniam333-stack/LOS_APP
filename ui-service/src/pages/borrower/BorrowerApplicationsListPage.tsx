import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listBorrowerApplications, type BorrowerAppSummary } from '@/api/borrowerPortal'
import { BorrowerContinueIntakeLink } from '@/components/borrower/BorrowerContinueIntakeLink'
import { ApiError } from '@/api/http'
import { PageHeader } from '@/components/PageHeader'
import { loanProductLabel } from '@/catalog/loanProducts'
import { formatInstant } from '@/lib/format'

export function BorrowerApplicationsListPage() {
  const [list, setList] = useState<BorrowerAppSummary[]>([])
  const [err, setErr] = useState<string | null>(null)

  const load = useCallback(() => {
    void listBorrowerApplications(0, 50)
      .then((p) => setList(p.content))
      .catch((e) => setErr(e instanceof ApiError ? e.message : 'Failed to list'))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (err && list.length === 0) {
    return <p className="text-sm text-rose-700">{err}</p>
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Loan applications" description="View your submitted and draft loan applications." />
      {err ? (
        <p className="text-sm text-rose-700" role="alert">
          {err}
        </p>
      ) : null}
      {list.length === 0 ? (
        <div className="rounded-lg border border-slate-200 bg-white p-10 text-center text-sm text-slate-600 shadow-sm">
          No applications found.
        </div>
      ) : (
        <div className="bt-card overflow-x-auto shadow-sm">
          <table className="bt-table min-w-full">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-600">
              <tr>
                <th className="whitespace-nowrap px-5 py-3.5">Application</th>
                <th className="whitespace-nowrap px-5 py-3.5">Product</th>
                <th className="whitespace-nowrap px-5 py-3.5">Status</th>
                <th className="whitespace-nowrap px-5 py-3.5">Updated</th>
                <th className="whitespace-nowrap px-5 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="">
              {list.map((a) => (
                <tr key={a.applicationId} className="">
                  <td className="whitespace-nowrap px-5 py-4 font-medium text-slate-900">{a.applicationNumber}</td>
                  <td className="px-5 py-4 text-slate-700">{loanProductLabel(a.product)}</td>
                  <td className="px-5 py-4">
                    <span className="inline-flex rounded-md border border-slate-200 bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-800">
                      {a.friendlyStatus}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-slate-600 tabular-nums">{formatInstant(a.updatedAt)}</td>
                  <td className="px-5 py-4">
                    <div className="flex items-center justify-end gap-3">
                      <BorrowerContinueIntakeLink
                        applicationId={a.applicationId}
                        status={a.status}
                        label="Continue"
                        className="text-sm font-medium text-indigo-800 underline-offset-2 hover:underline"
                      />
                      <Link
                        to={`/borrower/applications/${a.applicationId}`}
                        className="text-sm font-medium text-slate-800 underline-offset-2 hover:underline"
                      >
                        Open
                      </Link>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
