/**
 * Aligns with backend `KycStepResultResponse` (JSON from Karza/Authbridge orchestration).
 */
export interface KycStepResultResponse {
  id: string
  applicationId: string
  partyId?: string | null
  stepType: string
  provider: string
  outcome: 'SUCCESS' | 'FAILURE' | 'PENDING' | 'MANUAL' | string
  confidenceScore: number
  parsedData: Record<string, unknown> | null
  transactionId: string | null
  errorMessage: string | null
  overridden: boolean
  overrideReason: string | null
  attemptNumber: number
  createdAt: string | null
  completedAt: string | null
}
