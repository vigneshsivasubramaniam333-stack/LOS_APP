import type { ApplicationResponse } from '@/types/application'
import type { LoanProductCode } from '@/types/createApplication'
import { ANCHOR_BORROWER_TYPE } from './anchorIntakeConstants'
import { contactsFromBusinessInfo, primaryContactFromCorporate } from './anchorContacts'
import { createEmptyAnchorFormState, type AnchorFormState } from './anchorIntakeTypes'

/**
 * Fills the anchor intake form from a GET /applications response for Continue intake.
 */
export function hydrateAnchorFormFromApplication(
  app: ApplicationResponse,
  base: AnchorFormState = createEmptyAnchorFormState(),
): AnchorFormState {
  const pi = (app.personalInfo ?? {}) as Record<string, unknown>
  const bi = (app.businessInfo ?? {}) as Record<string, unknown>
  const fi = (app.financialInfo ?? {}) as Record<string, unknown>
  const str = (o: Record<string, unknown>, k: string) =>
    o[k] != null && o[k] !== undefined ? String(o[k]) : ''
  const toBool = (o: Record<string, unknown>, k: string) =>
    o[k] === true || String(o[k] ?? '').toLowerCase() === 'true'

  const corporateName = str(bi, 'corporateName') || str(bi, 'businessName') || base.corporateName
  const email = str(bi, 'email') || str(pi, 'email') || base.email
  const mobile = str(bi, 'mobile') || str(pi, 'mobile') || str(pi, 'phone') || base.mobile
  const savedContacts = contactsFromBusinessInfo(bi.contacts)
  const contacts =
    savedContacts.length > 0
      ? savedContacts
      : email || mobile
        ? [
            primaryContactFromCorporate({
              name: str(bi, 'accountHolderName') || corporateName,
              email,
              mobile,
            }),
          ]
        : []

  return {
    ...base,
    borrowerType: ANCHOR_BORROWER_TYPE,
    loanProduct: (app.loanProduct as LoanProductCode) || base.loanProduct,
    requestedAmount: app.requestedAmount != null ? String(app.requestedAmount) : base.requestedAmount,
    tenureMonths: app.tenureMonths != null ? String(app.tenureMonths) : base.tenureMonths,
    purpose: str(pi, 'purpose') || base.purpose,
    corporateName,
    email,
    mobile,
    dateOfIncorporation: str(bi, 'dateOfIncorporation') || base.dateOfIncorporation,
    addressLine: str(bi, 'addressLine') || str(bi, 'businessAddress') || base.addressLine,
    city: str(bi, 'city') || base.city,
    state: str(bi, 'state') || base.state,
    country: str(bi, 'country') || base.country || 'India',
    pincode: str(bi, 'pincode') || base.pincode,
    entityPan: str(bi, 'entityPan') || str(pi, 'panNumber') || base.entityPan,
    gstin: str(bi, 'gstin') || base.gstin,
    cin: str(bi, 'cin') || base.cin,
    bankAccountNumber:
      str(bi, 'bankAccountNumber') || str(pi, 'bankAccountNumber') || str(fi, 'accountNumber') || base.bankAccountNumber,
    ifscCode: str(bi, 'ifscCode') || str(pi, 'ifsc') || str(fi, 'ifsc') || base.ifscCode,
    accountHolderName: str(bi, 'accountHolderName') || str(pi, 'fullName') || base.accountHolderName,
    consentKyc: toBool(fi, 'consentKyc') || toBool(pi, 'consentKyc') || base.consentKyc,
    consentBureau: toBool(fi, 'consentBureau') || toBool(pi, 'consentBureau') || base.consentBureau,
    consentAccountAggregator: toBool(fi, 'consentAccountAggregator') || base.consentAccountAggregator,
    consentComms: toBool(fi, 'consentComms') || toBool(pi, 'consentComms') || base.consentComms,
    documentUploaded: { ...base.documentUploaded },
    contacts,
  }
}
