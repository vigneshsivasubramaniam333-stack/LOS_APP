import type { BorrowerType, LoanProductCode } from '@/types/createApplication'
import { ANCHOR_BORROWER_TYPE } from './anchorIntakeConstants'
import type { AnchorContactUser } from './anchorContacts'

export interface AnchorFormState {
  borrowerType: BorrowerType
  loanProduct: LoanProductCode
  requestedAmount: string
  tenureMonths: string
  purpose: string
  corporateName: string
  email: string
  mobile: string
  dateOfIncorporation: string
  addressLine: string
  city: string
  state: string
  country: string
  pincode: string
  entityPan: string
  gstin: string
  cin: string
  bankAccountNumber: string
  ifscCode: string
  accountHolderName: string
  consentKyc: boolean
  consentBureau: boolean
  consentAccountAggregator: boolean
  consentComms: boolean
  documentUploaded: Record<string, boolean>
  /** Workflow-configured contacts/users (persisted on businessInfo.contacts). */
  contacts: AnchorContactUser[]
}

export function createEmptyAnchorFormState(): AnchorFormState {
  return {
    borrowerType: ANCHOR_BORROWER_TYPE,
    loanProduct: 'BUSINESS_WC_INVOICE_DISCOUNTING',
    requestedAmount: '',
    tenureMonths: '',
    purpose: '',
    corporateName: '',
    email: '',
    mobile: '',
    dateOfIncorporation: '',
    addressLine: '',
    city: '',
    state: '',
    country: 'India',
    pincode: '',
    entityPan: '',
    gstin: '',
    cin: '',
    bankAccountNumber: '',
    ifscCode: '',
    accountHolderName: '',
    consentKyc: false,
    consentBureau: false,
    consentAccountAggregator: false,
    consentComms: false,
    documentUploaded: {},
    contacts: [],
  }
}
