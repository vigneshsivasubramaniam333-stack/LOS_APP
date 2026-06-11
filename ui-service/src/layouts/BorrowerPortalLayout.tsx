import { useEffect, useState } from 'react'
import { Link, NavLink, Navigate, Outlet, useNavigate } from 'react-router-dom'
import { getBorrowerDashboard, getBorrowerNotifications } from '@/api/borrowerPortal'
import { useAuth } from '@/auth/useAuth'
import { isBorrowerRole } from '@/auth/types'
import { BrandLogo } from '@/components/BrandLogo'

const COMPANY_NAME = 'Credinnov'

const nav = [
  { to: '/borrower/dashboard', label: 'Dashboard' },
  { to: '/borrower/apply', label: 'Apply for loan' },
  { to: '/borrower/applications', label: 'Loan applications' },
  { to: '/borrower/invoice-discounting', label: 'Invoice discounting' },
  { to: '/borrower/documents', label: 'Signed documents' },
  { to: '/borrower/profile', label: 'Profile' },
] as const

const linkClass = ({ isActive }: { isActive: boolean }) =>
  [
    'block rounded-md px-3 py-2 text-sm font-medium transition-colors',
    isActive
      ? 'bg-bl-primary text-white shadow-sm'
      : 'text-white/80 hover:bg-white/10 hover:text-white',
  ].join(' ')

function userInitials(name: string) {
  const p = name.trim().split(/\s+/)
  if (p.length >= 2) return (p[0]![0]! + p[1]![0]!).toUpperCase()
  return name.slice(0, 2).toUpperCase() || '—'
}

export function BorrowerPortalLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [hasDisbursedLoan, setHasDisbursedLoan] = useState(false)
  const [loanId, setLoanId] = useState<string | null>(null)
  const [notifOpen, setNotifOpen] = useState(false)
  const [notifs, setNotifs] = useState<Awaited<ReturnType<typeof getBorrowerNotifications>>>([])

  useEffect(() => {
    let c = true
    void (async () => {
      try {
        const d = await getBorrowerDashboard()
        if (!c) return
        setHasDisbursedLoan(d.activeLoanCount > 0)
        setLoanId(d.primaryDisbursedApplicationId)
        const n = await getBorrowerNotifications()
        if (c) setNotifs(n)
      } catch {
        if (c) {
          setHasDisbursedLoan(false)
          setLoanId(null)
          setNotifs([])
        }
      }
    })()
    return () => {
      c = false
    }
  }, [user?.userId])

  if (!user || !isBorrowerRole(user.role)) {
    return null
  }
  if (user.passwordResetRequired) {
    return <Navigate to="/borrower/change-password" replace />
  }

  return (
    <div className="min-h-screen bg-bl-canvas">
      <aside className="fixed left-0 top-0 z-40 flex h-screen w-60 flex-col border-r border-white/5 bg-bl-navy">
        <div className="shrink-0 border-b border-white/10 px-3 py-4">
          <div className="w-full">
            <div className="flex min-h-[2.25rem] w-full items-center" title="Billionloans">
              <BrandLogo variant="billionloans" tone="light" className="text-lg" />
            </div>
            <div className="mt-2 text-xs font-medium leading-tight text-white/50">Borrower portal</div>
          </div>
        </div>
        <nav className="flex-1 space-y-0.5 overflow-y-auto p-2" aria-label="Borrower">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={linkClass}
              end={item.to === '/borrower/dashboard'}
            >
              {item.label}
            </NavLink>
          ))}
          {hasDisbursedLoan && loanId ? (
            <>
              <div className="px-3 pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wide text-white/40">
                After disbursement
              </div>
              <NavLink to={`/borrower/loans/${loanId}/repayment`} className={linkClass}>
                Repayment schedule
              </NavLink>
              <NavLink to={`/borrower/loans/${loanId}/statement`} className={linkClass}>
                Statement
              </NavLink>
              <NavLink to={`/borrower/loans/${loanId}/transactions`} className={linkClass}>
                Transactions
              </NavLink>
            </>
          ) : null}
        </nav>
        <div className="shrink-0 border-t border-white/10 p-2">
          <Link
            to="/"
            className="block rounded-md px-3 py-2 text-sm font-medium text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          >
            Staff sign-in
          </Link>
        </div>
      </aside>
      <div className="flex min-h-screen flex-col pl-60">
        <header className="sticky top-0 z-30 border-b border-slate-200/80 bg-white/95 shadow-sm backdrop-blur">
          <div className="mx-auto flex min-h-14 w-full max-w-7xl flex-wrap items-center justify-between gap-3 px-5 py-2 sm:px-8">
            <div className="order-2 min-w-0 flex-1 text-center sm:flex-[2]">
              <p className="truncate text-sm font-medium text-slate-800">{COMPANY_NAME}</p>
            </div>
            <div className="order-1 flex items-center gap-2 sm:flex-1">
              {notifs.length > 0 ? (
                <div className="relative">
                  <button
                    type="button"
                    className="rounded-md border border-slate-200 bg-slate-50/80 px-3 py-1.5 text-sm text-slate-800 hover:bg-slate-50"
                    onClick={() => setNotifOpen((o) => !o)}
                    aria-expanded={notifOpen}
                  >
                    Notifications ({notifs.length})
                  </button>
                  {notifOpen ? (
                    <div className="absolute left-0 z-20 mt-1 w-80 max-w-[90vw] rounded-md border border-slate-200 bg-white p-2 shadow-lg">
                      <ul className="max-h-64 space-y-2 overflow-y-auto text-xs text-slate-800">
                        {notifs.map((n) => (
                          <li key={n.id} className="rounded border border-slate-100 p-2">
                            <div className="font-medium">{n.title}</div>
                            <div className="text-slate-600">{n.message}</div>
                            {n.applicationId ? (
                              <Link
                                to={`/borrower/applications/${n.applicationId}`}
                                className="mt-1 inline-block text-slate-800 underline"
                                onClick={() => setNotifOpen(false)}
                              >
                                Open application
                              </Link>
                            ) : null}
                          </li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                </div>
              ) : null}
            </div>
            <div className="order-3 flex items-center justify-end gap-2 sm:flex-1">
              <div
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-bl-primary text-xs font-semibold text-white"
                title={user.name}
              >
                {userInitials(user.name)}
              </div>
              <div className="hidden text-right sm:block">
                <div className="text-sm font-medium text-slate-900">{user.name}</div>
                <div className="text-xs text-slate-500">{user.email}</div>
              </div>
              <button
                type="button"
                className="shrink-0 rounded-md border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-800 hover:bg-slate-50"
                onClick={() => {
                  logout()
                  navigate('/borrower/login', { replace: true })
                }}
              >
                Log out
              </button>
            </div>
          </div>
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 px-5 py-6 sm:px-8 sm:py-8">
          <Outlet />
        </main>
        <footer className="mt-auto border-t border-slate-200/90 bg-white py-1.5">
          <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-center gap-1.5 px-5 sm:px-8">
            <span className="text-[11px] text-slate-500">Powered by</span>
            <BrandLogo variant="billiontech" tone="dark" className="text-xs" />
          </div>
        </footer>
      </div>
    </div>
  )
}
