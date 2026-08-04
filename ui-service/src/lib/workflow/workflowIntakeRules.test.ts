import { describe, expect, it } from 'vitest'
import { createEmptyIntakeFormState } from '@/lib/intake/intakeTypes'
import type { WorkflowConfigResponse } from '@/types/workflow'
import {
  intakeConfigFromApi,
  isWorkflowDrivenIntake,
  missingRequiredWorkflowDocuments,
  resolveDocumentSlots,
  validateMandatoryGroups,
  validateWorkflowAge,
  validateWorkflowKycStep,
  validateWorkflowTenure,
} from './workflowIntakeRules'

function wf(over: Partial<WorkflowConfigResponse>): WorkflowConfigResponse {
  return {
    id: '1',
    name: 'Test',
    borrowerType: 'INDIVIDUAL',
    loanProduct: 'PERSONAL_LOAN',
    steps: [],
    active: true,
    version: 1,
    createdAt: null,
    ...over,
  } as WorkflowConfigResponse
}

describe('workflowIntakeRules', () => {
  it('intakeConfigFromApi uses fallback when response omits intakeConfig', () => {
    const fallback = { policy: 'WORKFLOW_DRIVEN' as const }
    expect(intakeConfigFromApi(wf({}), fallback).policy).toBe('WORKFLOW_DRIVEN')
  })

  it('intakeConfigFromApi normalizes workflow-driven policy from API', () => {
    expect(intakeConfigFromApi(wf({ intakeConfig: { policy: 'WORKFLOW_DRIVEN' } })).policy).toBe(
      'WORKFLOW_DRIVEN',
    )
  })

  it('legacy policy bypasses workflow-driven validation', () => {
    const workflow = wf({ intakeConfig: { policy: 'LEGACY' } })
    expect(isWorkflowDrivenIntake(workflow)).toBe(false)
    expect(validateWorkflowKycStep(createEmptyIntakeFormState(), workflow)).toBeNull()
  })

  it('requires PAN when PAN_VERIFY step is mandatory', () => {
    const workflow = wf({
      intakeConfig: { policy: 'WORKFLOW_DRIVEN' },
      steps: [{ step: 'PAN_VERIFY', mandatory: true }],
    })
    const form = createEmptyIntakeFormState()
    expect(validateWorkflowKycStep(form, workflow)).toMatch(/PAN/)
    form.panNumber = 'ABCDE1234F'
    expect(validateWorkflowKycStep(form, workflow)).toBeNull()
  })

  it('ANY group accepts one of Aadhaar, Voter, DL', () => {
    const workflow = wf({
      intakeConfig: {
        policy: 'WORKFLOW_DRIVEN',
        mandatoryFieldGroups: [
          {
            id: 'g1',
            label: 'Government ID',
            logic: 'ANY',
            steps: ['AADHAAR_OTP', 'VOTER_ID_VERIFY', 'DL_VERIFY'],
          },
        ],
      },
      steps: [
        { step: 'AADHAAR_OTP', mandatory: true },
        { step: 'VOTER_ID_VERIFY', mandatory: true },
        { step: 'DL_VERIFY', mandatory: true },
      ],
    })
    const form = createEmptyIntakeFormState()
    expect(validateMandatoryGroups(form, workflow)).toMatch(/Government ID/)
    form.voterId = 'ABC1234567'
    expect(validateMandatoryGroups(form, workflow)).toBeNull()
  })

  it('validates age min/max', () => {
    const workflow = wf({
      intakeConfig: {
        policy: 'WORKFLOW_DRIVEN',
        ageRules: { enabled: true, minAge: 21, maxAge: 65 },
      },
    })
    const form = createEmptyIntakeFormState()
    form.dateOfBirth = '2010-01-01'
    expect(validateWorkflowAge(form, workflow)).toMatch(/at least 21/)
    form.dateOfBirth = '1990-06-15'
    expect(validateWorkflowAge(form, workflow)).toBeNull()
  })

  it('validates tenure dropdown', () => {
    const workflow = wf({
      intakeConfig: {
        policy: 'WORKFLOW_DRIVEN',
        tenureRules: {
          inputMode: 'dropdown',
          options: [
            { value: '90', label: '90 Days' },
            { value: '180', label: '180 Days' },
          ],
        },
      },
    })
    const form = createEmptyIntakeFormState()
    form.tenureMonths = '120'
    expect(validateWorkflowTenure(form, workflow)).toMatch(/allowed options/)
    form.tenureMonths = '90'
    expect(validateWorkflowTenure(form, workflow)).toBeNull()
  })

  it('flags missing required standalone documents', () => {
    const workflow = wf({
      intakeConfig: {
        policy: 'WORKFLOW_DRIVEN',
        standaloneDocuments: [{ documentType: 'PHOTOGRAPH', required: true, label: 'Photograph' }],
      },
      steps: [],
    })
    const form = createEmptyIntakeFormState()
    expect(missingRequiredWorkflowDocuments(form, workflow, 'INDIVIDUAL')).toContain('PHOTOGRAPH')
    form.documentUploaded.PHOTOGRAPH = true
    expect(missingRequiredWorkflowDocuments(form, workflow, 'INDIVIDUAL')).toEqual([])
  })

  it('merges standalone documents even when intake policy is not WORKFLOW_DRIVEN', () => {
    const workflow = wf({
      intakeConfig: {
        policy: 'LEGACY',
        standaloneDocuments: [{ documentType: 'BOARD_RESOLUTION', required: true, label: 'Board resolution' }],
      },
      steps: [],
    })
    const slots = resolveDocumentSlots(workflow, 'COMPANY')
    expect(slots.map((s) => s.documentType)).toEqual(expect.arrayContaining(['BOARD_RESOLUTION']))
    expect(slots.find((s) => s.documentType === 'BOARD_RESOLUTION')?.required).toBe(true)
    const form = createEmptyIntakeFormState()
    expect(missingRequiredWorkflowDocuments(form, workflow, 'COMPANY')).toContain('BOARD_RESOLUTION')
  })

  it('resolves voter and driving licence upload slots from workflow KYC steps', () => {
    const workflow = wf({
      intakeConfig: { policy: 'WORKFLOW_DRIVEN' },
      steps: [
        { step: 'VOTER_ID_VERIFY', mandatory: true },
        { step: 'DL_VERIFY', mandatory: true },
      ],
    })
    const slots = resolveDocumentSlots(workflow, 'INDIVIDUAL')
    expect(slots.map((s) => s.documentType)).toEqual(expect.arrayContaining(['VOTER_ID', 'DRIVING_LICENSE']))
    expect(slots.find((s) => s.documentType === 'VOTER_ID')?.label).toBe('Voter ID card')
    expect(slots.find((s) => s.documentType === 'DRIVING_LICENSE')?.label).toBe('Driving licence')
  })

  it('merges intakeConfig.esignSigningDocuments when collectAtIntake is true', () => {
    const workflow = wf({
      intakeConfig: {
        policy: 'WORKFLOW_DRIVEN',
        esignSigningDocuments: [
          {
            documentType: 'GST',
            label: 'GST certificate',
            required: true,
            collectAtIntake: true,
            expectedPageCount: 2,
          },
          {
            documentType: 'BOARD_RESOLUTION',
            label: 'Board resolution',
            required: true,
            collectAtIntake: false,
            expectedPageCount: 2,
          },
        ],
      },
      steps: [],
    })
    const slots = resolveDocumentSlots(workflow, 'COMPANY')
    expect(slots.map((s) => s.documentType)).toEqual(expect.arrayContaining(['GST']))
    expect(slots.map((s) => s.documentType)).not.toContain('BOARD_RESOLUTION')
    expect(slots.map((s) => s.documentType)).not.toContain('KFS_AGREEMENT')
    expect(slots.find((s) => s.documentType === 'GST')?.required).toBe(true)
    const form = createEmptyIntakeFormState()
    expect(missingRequiredWorkflowDocuments(form, workflow, 'COMPANY')).toContain('GST')
  })

  it('merges legacy esignDocuments additional when collectAtIntake is true', () => {
    const workflow = wf({
      intakeConfig: { policy: 'WORKFLOW_DRIVEN' },
      steps: [
        {
          step: 'ESIGN_AGREEMENT',
          esignDocuments: {
            defaultDocumentKey: 'KFS_AGREEMENT',
            expectedPageCount: 2,
            additional: [
              {
                documentType: 'GST',
                label: 'GST certificate',
                required: true,
                collectAtIntake: true,
                expectedPageCount: 2,
              },
            ],
          },
        },
      ],
    })
    const slots = resolveDocumentSlots(workflow, 'COMPANY')
    expect(slots.map((s) => s.documentType)).toEqual(expect.arrayContaining(['GST']))
    expect(slots.find((s) => s.documentType === 'GST')?.required).toBe(true)
    const form = createEmptyIntakeFormState()
    expect(missingRequiredWorkflowDocuments(form, workflow, 'COMPANY')).toContain('GST')
  })

  it('does not force esign default KFS document at intake', () => {
    const workflow = wf({
      intakeConfig: { policy: 'WORKFLOW_DRIVEN' },
      steps: [
        {
          step: 'ESIGN_AGREEMENT',
          esignDocuments: {
            defaultDocumentKey: 'KFS_AGREEMENT',
            additional: [],
          },
        },
      ],
    })
    const slots = resolveDocumentSlots(workflow, 'INDIVIDUAL')
    expect(slots.map((s) => s.documentType)).not.toContain('KFS_AGREEMENT')
  })
})
