import { http } from './http'

export interface BorrowerInvoiceItem {
  invoiceId: string
  invoiceNumber: string | null
  invoiceDate: string | null
  dueDate: string | null
  invoiceAmount: number | null
  netAmount: number | null
  eligibleAmount: number | null
  availableAmount: number | null
  status: string | null
  friendlyStatus: string
  programId: string | null
  anchorId: string | null
  flowType: string | null
  acceptable: boolean
  financeable: boolean
  maxFinanceableAmount: number | null
  suggestedFinanceAmount: number | null
}

export interface BorrowerInvoiceRepayment {
  repaymentId: string | null
  paidAt: string | null
  amount: number | null
  reference: string | null
  source: string | null
  status: string | null
  paymentMode: string | null
  friendlySource: string | null
}

export interface BorrowerInvoiceLoan {
  loanId: string
  loanNumber: string | null
  invoiceId: string | null
  status: string | null
  friendlyStatus: string
  requestedAmount: number | null
  sanctionedAmount: number | null
  disbursedAmount: number | null
  outstandingAmount: number | null
  totalRepayable: number | null
  totalRepaid: number | null
  dueDate: string | null
  repayable: boolean
}

export interface BorrowerInvoiceDiscounting {
  available: boolean
  message: string | null
  invoices: BorrowerInvoiceItem[]
  loans: BorrowerInvoiceLoan[]
}

export async function getInvoiceDiscounting(): Promise<BorrowerInvoiceDiscounting> {
  const { data } = await http.get<BorrowerInvoiceDiscounting>('/borrower/invoice-discounting')
  return data
}

export async function acceptInvoice(invoiceId: string): Promise<BorrowerInvoiceDiscounting> {
  const { data } = await http.post<BorrowerInvoiceDiscounting>(
    `/borrower/invoice-discounting/invoices/${invoiceId}/accept`,
  )
  return data
}

export async function requestInvoiceFinance(
  invoiceId: string,
  amount: number,
): Promise<BorrowerInvoiceDiscounting> {
  const { data } = await http.post<BorrowerInvoiceDiscounting>(
    `/borrower/invoice-discounting/invoices/${invoiceId}/finance`,
    { amount },
  )
  return data
}

export async function getLoanRepayments(loanId: string): Promise<BorrowerInvoiceRepayment[]> {
  const { data } = await http.get<BorrowerInvoiceRepayment[]>(
    `/borrower/invoice-discounting/loans/${loanId}/repayments`,
  )
  return data
}

export async function repayInvoiceLoan(
  loanId: string,
  amount: number,
): Promise<BorrowerInvoiceDiscounting> {
  const { data } = await http.post<BorrowerInvoiceDiscounting>(
    `/borrower/invoice-discounting/loans/${loanId}/repay`,
    { amount },
  )
  return data
}
