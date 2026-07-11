import { http } from './http'
import type { PlpProgramSetupResponse } from '@/types/plp'

export type ProgramApprovalStatus =
  | 'DRAFT'
  | 'PENDING_L2'
  | 'SENT_BACK'
  | 'APPROVED'
  | 'REJECTED'

export interface ProgramApprovalResponse {
  programId: string
  programName: string
  programCode: string
  approvalStatus: ProgramApprovalStatus
  assignedL1UserId: string | null
  assignedL1UserName: string | null
  assignedL2UserId: string | null
  assignedL2UserName: string | null
  approvalNotes: string | null
  anchorApplicationId: string | null
  approvedAt: string | null
  approvedByUserId: string | null
  plpOperationalStatus: string | null
}

export async function listPendingProgramApprovals(): Promise<ProgramApprovalResponse[]> {
  const { data } = await http.get<ProgramApprovalResponse[]>('/plp/programs/approvals/pending')
  return data
}

export async function getProgramApproval(programId: string): Promise<ProgramApprovalResponse> {
  const { data } = await http.get<ProgramApprovalResponse>(`/plp/programs/${programId}/approval`)
  return data
}

export async function refreshPlpProgramStatus(programId: string): Promise<ProgramApprovalResponse> {
  const { data } = await http.post<ProgramApprovalResponse>(
    `/plp/programs/${programId}/refresh-plp-status`,
  )
  return data
}

export async function submitProgramToL2(programId: string): Promise<ProgramApprovalResponse> {
  const { data } = await http.post<ProgramApprovalResponse>(
    `/plp/programs/${programId}/approval/submit-to-l2`,
  )
  return data
}

export async function sendBackProgram(programId: string, notes: string): Promise<ProgramApprovalResponse> {
  const { data } = await http.post<ProgramApprovalResponse>(
    `/plp/programs/${programId}/approval/send-back`,
    { notes },
  )
  return data
}

export async function approveProgram(programId: string): Promise<ProgramApprovalResponse> {
  const { data } = await http.post<ProgramApprovalResponse>(
    `/plp/programs/${programId}/approval/approve`,
  )
  return data
}

export async function notifyBorrowerToComplete(
  applicationId: string,
  completedStep = 1,
): Promise<void> {
  await http.post(`/applications/${applicationId}/notify-borrower`, null, {
    params: { completedStep },
  })
}

export async function acceptBorrowerSubmission(applicationId: string) {
  const { data } = await http.post(`/applications/${applicationId}/review/accept`)
  return data
}

export async function sendBackBorrowerSubmission(applicationId: string, notes: string) {
  const { data } = await http.post(`/applications/${applicationId}/review/send-back`, { notes })
  return data
}

export async function submitDelegatedBorrowerIntake(applicationId: string): Promise<void> {
  await http.post(`/borrower/applications/${applicationId}/submit-delegated`)
}
