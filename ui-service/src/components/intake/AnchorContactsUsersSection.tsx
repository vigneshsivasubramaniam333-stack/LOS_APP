import type { AnchorContactRole, AnchorContactUser } from '@/lib/intake/anchorContacts'
import { ANCHOR_CONTACT_ROLE_OPTIONS, createEmptyAnchorContact } from '@/lib/intake/anchorContacts'

const fieldLabel = 'block text-sm text-slate-700'
const fieldCaption = 'mb-1 block text-xs font-medium text-slate-500'
const fieldInput = 'bt-input w-full'

export function AnchorContactsUsersSection({
  contacts,
  maxUsers,
  onChange,
  disabled = false,
}: {
  contacts: AnchorContactUser[]
  maxUsers: number
  onChange: (next: AnchorContactUser[]) => void
  disabled?: boolean
}) {
  const signingCount = contacts.filter((c) => c.isSigningAuthority).length
  const canAdd = contacts.length < maxUsers

  function patchAt(index: number, partial: Partial<AnchorContactUser>) {
    onChange(contacts.map((c, i) => (i === index ? { ...c, ...partial } : c)))
  }

  function removeAt(index: number) {
    if (contacts.length <= 1) return
    onChange(contacts.filter((_, i) => i !== index))
  }

  function addUser() {
    if (!canAdd) return
    onChange([
      ...contacts,
      createEmptyAnchorContact({
        role: 'MAKER',
        isSigningAuthority: false,
        signingOrder: 0,
      }),
    ])
  }

  return (
    <section className="space-y-4">
      <div>
        <h2 className="bt-card-title">Users</h2>
        <p className="mt-1 text-xs text-slate-600">
          Capture portal users for this anchor. Mark signing authorities for e-sign invitations
          {signingCount > 1 ? '; set signing order when more than one authority is selected' : ''}. Max{' '}
          {maxUsers} user{maxUsers === 1 ? '' : 's'}.
        </p>
      </div>
      <ul className="space-y-4">
        {contacts.map((c, index) => (
          <li key={c.id} className="rounded-lg border border-slate-200 bg-slate-50/60 p-4">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-medium text-slate-900">
                User {index + 1}
                {index === 0 ? (
                  <span className="ml-2 text-xs font-normal text-slate-500">(primary / corporate contact)</span>
                ) : null}
              </p>
              {index > 0 && !disabled ? (
                <button
                  type="button"
                  className="text-xs font-medium text-red-700 hover:underline"
                  onClick={() => removeAt(index)}
                >
                  Remove
                </button>
              ) : null}
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className={fieldLabel}>
                <span className={fieldCaption}>Name *</span>
                <input
                  className={fieldInput}
                  disabled={disabled}
                  value={c.name}
                  onChange={(e) => patchAt(index, { name: e.target.value })}
                />
              </label>
              <label className={fieldLabel}>
                <span className={fieldCaption}>Role *</span>
                <select
                  className={`${fieldInput} bg-white`}
                  disabled={disabled}
                  value={c.role}
                  onChange={(e) => patchAt(index, { role: e.target.value as AnchorContactRole })}
                >
                  {ANCHOR_CONTACT_ROLE_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className={fieldLabel}>
                <span className={fieldCaption}>Email *</span>
                <input
                  type="email"
                  className={fieldInput}
                  disabled={disabled}
                  value={c.email}
                  onChange={(e) => patchAt(index, { email: e.target.value })}
                />
              </label>
              <label className={fieldLabel}>
                <span className={fieldCaption}>Mobile *</span>
                <input
                  className={fieldInput}
                  disabled={disabled}
                  inputMode="tel"
                  value={c.mobile}
                  onChange={(e) =>
                    patchAt(index, { mobile: e.target.value.replace(/\D/g, '').slice(0, 12) })
                  }
                />
              </label>
              <label className="flex items-center gap-2 text-sm text-slate-800 sm:col-span-2">
                <input
                  type="checkbox"
                  disabled={disabled}
                  checked={c.isSigningAuthority}
                  onChange={(e) =>
                    patchAt(index, {
                      isSigningAuthority: e.target.checked,
                      signingOrder: e.target.checked ? Math.max(1, c.signingOrder) : 0,
                    })
                  }
                />
                <span>Signing authority</span>
              </label>
              {c.isSigningAuthority && signingCount > 1 ? (
                <label className={fieldLabel}>
                  <span className={fieldCaption}>Signing order *</span>
                  <input
                    type="number"
                    min={1}
                    max={signingCount}
                    className={fieldInput}
                    disabled={disabled}
                    value={c.signingOrder > 0 ? c.signingOrder : ''}
                    onChange={(e) =>
                      patchAt(index, {
                        signingOrder: Number.parseInt(e.target.value, 10) || 0,
                      })
                    }
                  />
                  <span className="mt-0.5 block text-[11px] text-slate-500">
                    E-sign invitation is sent to order 1 first.
                  </span>
                </label>
              ) : null}
            </div>
          </li>
        ))}
      </ul>
      {!disabled && canAdd ? (
        <button
          type="button"
          className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-800"
          onClick={addUser}
        >
          Add user
        </button>
      ) : null}
      {!canAdd ? (
        <p className="text-xs text-slate-500">Maximum of {maxUsers} users reached for this workflow.</p>
      ) : null}
    </section>
  )
}
