import { describe, expect, it } from 'vitest'
import {
  canAccessAdminConfigNav,
  canCreateOrNotifyBorrowerIntake,
  canHandOffToCo,
  canSendBackToBorrower,
  canSendBackToRm,
  isBorrowerRole,
} from './types'

describe('canAccessAdminConfigNav', () => {
  it('allows Administrator and Credit Manager', () => {
    expect(canAccessAdminConfigNav('ADMINISTRATOR')).toBe(true)
    expect(canAccessAdminConfigNav('CREDIT_MANAGER')).toBe(true)
  })
  it('hides for other staff roles', () => {
    expect(canAccessAdminConfigNav('CREDIT_OFFICER')).toBe(false)
    expect(canAccessAdminConfigNav('SALES_OFFICER')).toBe(false)
    expect(canAccessAdminConfigNav('BORROWER')).toBe(false)
  })
})

describe('canCreateOrNotifyBorrowerIntake', () => {
  it('allows RM and admins', () => {
    expect(canCreateOrNotifyBorrowerIntake('RELATIONSHIP_MANAGER')).toBe(true)
    expect(canCreateOrNotifyBorrowerIntake('ADMIN')).toBe(true)
    expect(canCreateOrNotifyBorrowerIntake('ADMINISTRATOR')).toBe(true)
  })
  it('blocks credit officer', () => {
    expect(canCreateOrNotifyBorrowerIntake('CREDIT_OFFICER')).toBe(false)
  })
})

describe('RM / CO review helpers', () => {
  it('hand off and send back to borrower are RM/admin', () => {
    expect(canHandOffToCo('RELATIONSHIP_MANAGER')).toBe(true)
    expect(canSendBackToBorrower('ADMIN')).toBe(true)
    expect(canHandOffToCo('CREDIT_OFFICER')).toBe(false)
    expect(canSendBackToBorrower('CREDIT_OFFICER')).toBe(false)
  })
  it('send back to RM is CO/admin', () => {
    expect(canSendBackToRm('CREDIT_OFFICER')).toBe(true)
    expect(canSendBackToRm('RELATIONSHIP_MANAGER')).toBe(false)
  })
})

describe('isBorrowerRole', () => {
  it('matches borrower', () => {
    expect(isBorrowerRole('BORROWER')).toBe(true)
    expect(isBorrowerRole('CREDIT_OFFICER')).toBe(false)
  })
})
