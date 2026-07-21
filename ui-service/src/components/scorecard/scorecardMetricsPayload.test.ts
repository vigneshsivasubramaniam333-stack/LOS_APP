import { describe, expect, it } from 'vitest'
import {
  buildCustomMetricsFromRequirements,
  buildScorecardMetricsPayload,
  readScorecardManualInputValue,
} from './ScorecardMetricsManualSection'

describe('scorecardMetricsPayload', () => {
  it('puts custom OTHER parameters into scorecardMetrics', () => {
    const values = { LoanToBusinessTurnoverRatio: '1.5', Age: '32' }
    const requirements = [
      { manualKey: 'LoanToBusinessTurnoverRatio' },
      { manualKey: 'Age' },
    ]
    const custom = buildCustomMetricsFromRequirements(values, requirements)
    const payload = buildScorecardMetricsPayload(values, custom)
    expect(payload.scorecardMetrics).toEqual({
      LoanToBusinessTurnoverRatio: 1.5,
      Age: 32,
    })
  })

  it('maps known OTHER keys to top-level payload fields', () => {
    const values = { residenceOwned: 'Y', residenceStability: '24' }
    const custom = buildCustomMetricsFromRequirements(values, [{ manualKey: 'residenceOwned' }])
    const payload = buildScorecardMetricsPayload(values, custom)
    expect(payload.residenceOwned).toBe('Y')
    expect(payload.residenceStability).toBe(24)
    expect(payload.scorecardMetrics).toBeUndefined()
  })

  it('reads saved custom values from scorecardMetrics map', () => {
    const manual = {
      scorecardMetrics: {
        LoanToBusinessTurnoverRatio: { value: 2.1 },
      },
    }
    expect(readScorecardManualInputValue(manual, 'LoanToBusinessTurnoverRatio')).toBe('2.1')
  })

  it('falls back to top-level manual keys for known fields', () => {
    const manual = {
      residenceOwned: { value: 'Y' },
    }
    expect(readScorecardManualInputValue(manual, 'residenceOwned')).toBe('Y')
  })
})
