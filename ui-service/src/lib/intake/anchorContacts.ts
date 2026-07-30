export type AnchorContactRole = 'ANCHOR_ADMIN' | 'MAKER' | 'CHECKER'

export const ANCHOR_CONTACT_ROLE_OPTIONS: { value: AnchorContactRole; label: string }[] = [
  { value: 'ANCHOR_ADMIN', label: 'Anchor admin' },
  { value: 'MAKER', label: 'Maker' },
  { value: 'CHECKER', label: 'Checker' },
]

export type AnchorContactUser = {
  id: string
  name: string
  email: string
  mobile: string
  role: AnchorContactRole
  isSigningAuthority: boolean
  /** 1-based order among signing authorities; ignored when not a signing authority. */
  signingOrder: number
}

export function createEmptyAnchorContact(partial?: Partial<AnchorContactUser>): AnchorContactUser {
  return {
    id: globalThis.crypto?.randomUUID?.() ?? `c-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    name: '',
    email: '',
    mobile: '',
    role: 'ANCHOR_ADMIN',
    isSigningAuthority: false,
    signingOrder: 0,
    ...partial,
  }
}

export function primaryContactFromCorporate(args: {
  name?: string
  email?: string
  mobile?: string
}): AnchorContactUser {
  return createEmptyAnchorContact({
    name: (args.name ?? '').trim(),
    email: (args.email ?? '').trim(),
    mobile: (args.mobile ?? '').trim().replace(/\D/g, '').slice(0, 12),
    role: 'ANCHOR_ADMIN',
    isSigningAuthority: true,
    signingOrder: 1,
  })
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** Normalize signingOrder for authorities (1..n) and clear order on non-authorities. */
export function normalizeContactSigningOrders(contacts: AnchorContactUser[]): AnchorContactUser[] {
  const authorities = contacts
    .filter((c) => c.isSigningAuthority)
    .slice()
    .sort((a, b) => {
      const ao = a.signingOrder > 0 ? a.signingOrder : Number.MAX_SAFE_INTEGER
      const bo = b.signingOrder > 0 ? b.signingOrder : Number.MAX_SAFE_INTEGER
      return ao - bo
    })
  const orderById = new Map<string, number>()
  authorities.forEach((c, i) => orderById.set(c.id, i + 1))
  return contacts.map((c) => ({
    ...c,
    signingOrder: c.isSigningAuthority ? (orderById.get(c.id) ?? 0) : 0,
  }))
}

export function validateAnchorContacts(
  contacts: AnchorContactUser[],
  maxUsers: number,
): string | null {
  if (contacts.length < 1) {
    return 'At least one user is required.'
  }
  if (maxUsers > 0 && contacts.length > maxUsers) {
    return `At most ${maxUsers} users are allowed for this workflow.`
  }
  const emails = new Set<string>()
  const mobiles = new Set<string>()
  let signingCount = 0
  for (let i = 0; i < contacts.length; i++) {
    const c = contacts[i]!
    const label = `User ${i + 1}`
    if (!c.name.trim()) return `${label}: name is required.`
    if (!c.email.trim() || !EMAIL_RE.test(c.email.trim())) return `${label}: enter a valid email.`
    const mobileDigits = c.mobile.replace(/\D/g, '')
    if (mobileDigits.length < 10) return `${label}: enter a valid 10-digit mobile.`
    if (!c.role) return `${label}: role is required.`
    const emailKey = c.email.trim().toLowerCase()
    if (emails.has(emailKey)) return `Duplicate email: ${c.email.trim()}`
    emails.add(emailKey)
    if (mobiles.has(mobileDigits)) return `Duplicate mobile: ${mobileDigits}`
    mobiles.add(mobileDigits)
    if (c.isSigningAuthority) signingCount += 1
  }
  if (signingCount < 1) {
    return 'Select at least one signing authority.'
  }
  if (signingCount > 1) {
    const orders = contacts
      .filter((c) => c.isSigningAuthority)
      .map((c) => c.signingOrder)
      .filter((n) => n > 0)
    if (orders.length !== signingCount) {
      return 'Set signing order for every signing authority.'
    }
    const unique = new Set(orders)
    if (unique.size !== orders.length) {
      return 'Signing order must be unique among signing authorities.'
    }
  }
  return null
}

export function contactsToBusinessInfoPayload(contacts: AnchorContactUser[]): Record<string, unknown>[] {
  return normalizeContactSigningOrders(contacts).map((c) => ({
    id: c.id,
    name: c.name.trim(),
    email: c.email.trim().toLowerCase(),
    mobile: c.mobile.replace(/\D/g, '').slice(0, 12),
    role: c.role,
    isSigningAuthority: c.isSigningAuthority,
    signingOrder: c.isSigningAuthority ? c.signingOrder : 0,
  }))
}

export function contactsFromBusinessInfo(raw: unknown): AnchorContactUser[] {
  if (!Array.isArray(raw) || raw.length === 0) return []
  const out: AnchorContactUser[] = []
  for (const row of raw) {
    if (!row || typeof row !== 'object') continue
    const m = row as Record<string, unknown>
    const roleRaw = String(m.role ?? 'ANCHOR_ADMIN').toUpperCase()
    const role: AnchorContactRole =
      roleRaw === 'MAKER' || roleRaw === 'CHECKER' || roleRaw === 'ANCHOR_ADMIN'
        ? roleRaw
        : 'ANCHOR_ADMIN'
    out.push(
      createEmptyAnchorContact({
        id: String(m.id ?? '') || undefined,
        name: String(m.name ?? ''),
        email: String(m.email ?? ''),
        mobile: String(m.mobile ?? '').replace(/\D/g, ''),
        role,
        isSigningAuthority: m.isSigningAuthority === true || String(m.isSigningAuthority) === 'true',
        signingOrder: Number.parseInt(String(m.signingOrder ?? '0'), 10) || 0,
      }),
    )
  }
  return normalizeContactSigningOrders(out)
}
