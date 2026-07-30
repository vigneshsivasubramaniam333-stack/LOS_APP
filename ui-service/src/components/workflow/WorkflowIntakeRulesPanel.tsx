import { useEffect, useMemo, useState } from 'react'
import type {
  WorkflowCoApplicantConfig,
  WorkflowCodedOption,
  WorkflowContactsConfig,
  WorkflowIntakeConfig,
  WorkflowMandatoryFieldGroup,
  WorkflowStandaloneDocument,
} from '@/types/workflow'
import type { VisualWorkflowStep } from '@/lib/workflowVisual'
import { KYC_IDENTITY_WORKFLOW_STEPS } from '@/lib/workflowVisual'
import { DEFAULT_LOAN_PURPOSE_OPTIONS, DEFAULT_OCCUPATION_OPTIONS } from '@/lib/intake/intakeOptionCatalogs'
import { defaultWorkflowDrivenIntakeConfig, newMandatoryGroup, newStandaloneDocument } from '@/lib/workflow/workflowIntakeRules'
import { ensureGeoStatesLoaded } from '@/lib/intake/masterGeoClientCache'
import type { GeoStateRow } from '@/api/geoMaster'

function AllowedStatesCheckboxSection({
  selected,
  onChange,
}: {
  selected: string[]
  onChange: (next: string[]) => void
}) {
  const [states, setStates] = useState<GeoStateRow[]>([])
  const [loadErr, setLoadErr] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadErr(null)
    ensureGeoStatesLoaded()
      .then((rows) => {
        if (!cancelled) setStates([...rows].sort((a, b) => a.stateName.localeCompare(b.stateName)))
      })
      .catch(() => {
        if (!cancelled) setLoadErr('Unable to load states from geo master.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const selectedSet = useMemo(
    () => new Set(selected.map((s) => s.trim().toLowerCase()).filter(Boolean)),
    [selected],
  )
  const allSelected = states.length > 0 && states.every((s) => selectedSet.has(s.stateName.toLowerCase()))

  function toggle(stateName: string, checked: boolean) {
    const key = stateName.toLowerCase()
    const without = selected.filter((s) => s.trim().toLowerCase() !== key)
    onChange(checked ? [...without, stateName].sort((a, b) => a.localeCompare(b)) : without)
  }

  function selectAll() {
    onChange(states.map((s) => s.stateName))
  }

  function clearAll() {
    onChange([])
  }

  return (
    <div className="mt-2 space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs disabled:opacity-50"
          disabled={loading || states.length === 0}
          onClick={selectAll}
        >
          Select all states
        </button>
        <button
          type="button"
          className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs disabled:opacity-50"
          disabled={loading || selected.length === 0}
          onClick={clearAll}
        >
          Clear selection
        </button>
        <span className="text-xs text-slate-500">
          {loading
            ? 'Loading states…'
            : allSelected
              ? 'All states selected'
              : selected.length === 0
                ? 'None selected (all states allowed at intake)'
                : `${selected.length} of ${states.length} selected`}
        </span>
      </div>
      {loadErr ? <p className="text-xs text-amber-800">{loadErr}</p> : null}
      <div className="max-h-56 overflow-y-auto rounded border border-slate-200 bg-white p-2">
        <div className="grid gap-1 sm:grid-cols-2 lg:grid-cols-3">
          {states.map((s) => {
            const checked = selectedSet.has(s.stateName.toLowerCase())
            return (
              <label
                key={s.id}
                className="flex items-center gap-2 rounded px-1.5 py-1 text-xs text-slate-800 hover:bg-slate-50"
              >
                <input
                  type="checkbox"
                  className="rounded border-slate-300"
                  checked={checked}
                  disabled={loading}
                  onChange={(e) => toggle(s.stateName, e.target.checked)}
                />
                <span>{s.stateName}</span>
              </label>
            )
          })}
        </div>
      </div>
    </div>
  )
}

function CodedOptionsEditor({
  title,
  description,
  options,
  onChange,
}: {
  title: string
  description: string
  options: WorkflowCodedOption[]
  onChange: (options: WorkflowCodedOption[]) => void
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
      <h3 className="text-sm font-medium text-slate-800">{title}</h3>
      <p className="mt-1 text-xs text-slate-500">{description}</p>
      <div className="mt-2 space-y-2">
        {options.map((opt, idx) => (
          <div key={`${opt.value}-${idx}`} className="flex flex-wrap items-end gap-2">
            <label className="block text-xs text-slate-600">
              Label
              <input
                className="mt-0.5 block w-44 rounded border border-slate-300 px-2 py-1 text-sm"
                value={opt.label}
                onChange={(e) => {
                  const next = [...options]
                  next[idx] = { ...opt, label: e.target.value }
                  onChange(next)
                }}
              />
            </label>
            <label className="block text-xs text-slate-600">
              Code
              <input
                className="mt-0.5 block w-40 rounded border border-slate-300 px-2 py-1 text-sm font-mono"
                value={opt.value}
                onChange={(e) => {
                  const next = [...options]
                  next[idx] = { ...opt, value: e.target.value }
                  onChange(next)
                }}
              />
            </label>
            <button
              type="button"
              className="text-xs text-rose-700"
              onClick={() => onChange(options.filter((_, i) => i !== idx))}
            >
              Remove
            </button>
          </div>
        ))}
        <button
          type="button"
          className="rounded border border-slate-300 bg-white px-2 py-1 text-xs"
          onClick={() =>
            onChange([
              ...options,
              { value: `OPTION_${options.length + 1}`, label: 'New option' },
            ])
          }
        >
          Add option
        </button>
      </div>
    </section>
  )
}

function intakeSummary(config: WorkflowIntakeConfig): string {
  const parts: string[] = []
  if (config.policy === 'WORKFLOW_DRIVEN') {
    parts.push('Workflow-driven')
  } else {
    parts.push('Legacy')
  }
  if (config.ageRules?.enabled) {
    parts.push(`Age ${config.ageRules.minAge ?? '?'}-${config.ageRules.maxAge ?? '?'}`)
  }
  if (config.tenureRules?.inputMode === 'dropdown') {
    parts.push('Tenure: dropdown')
  }
  if ((config.locationRules?.allowedStates?.length ?? 0) > 0) {
    parts.push(`States: ${config.locationRules!.allowedStates!.length} allowed`)
  }
  if ((config.mandatoryFieldGroups?.length ?? 0) > 0) {
    parts.push(`${config.mandatoryFieldGroups?.length} OR group(s)`)
  }
  return parts.join(' · ')
}

export function WorkflowIntakeRulesPanel({
  intakeConfig,
  onChange,
  visualSteps,
  hideCoApplicant = false,
  showContacts = false,
}: {
  intakeConfig: WorkflowIntakeConfig
  onChange: (next: WorkflowIntakeConfig) => void
  visualSteps: VisualWorkflowStep[]
  hideCoApplicant?: boolean
  /** When true (ANCHOR workflows), show contacts/users capture config. */
  showContacts?: boolean
}) {
  const configuredSteps = visualSteps.map((s) => s.step)

  function patch(partial: Partial<WorkflowIntakeConfig>) {
    onChange({ ...intakeConfig, ...partial })
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <span
          className={
            intakeConfig.policy === 'WORKFLOW_DRIVEN'
              ? 'bt-badge bt-badge-green'
              : 'bt-badge bt-badge-gray'
          }
        >
          {intakeConfig.policy === 'WORKFLOW_DRIVEN' ? 'Workflow-driven intake' : 'Legacy intake'}
        </span>
        <span className="text-xs text-slate-500">{intakeSummary(intakeConfig)}</span>
      </div>

      <label className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">Intake policy</span>
        <select
          className="bt-input w-full max-w-md"
          value={intakeConfig.policy ?? 'LEGACY'}
          onChange={(e) => {
            const policy = e.target.value as 'LEGACY' | 'WORKFLOW_DRIVEN'
            if (policy === 'WORKFLOW_DRIVEN') {
              onChange({ ...intakeConfig, ...defaultWorkflowDrivenIntakeConfig(), policy: 'WORKFLOW_DRIVEN' })
            } else {
              onChange({ ...intakeConfig, policy: 'LEGACY' })
            }
          }}
        >
          <option value="LEGACY">Legacy (existing hardcoded intake rules)</option>
          <option value="WORKFLOW_DRIVEN">Workflow-driven (KYC steps gate fields & documents)</option>
        </select>
      </label>

      {intakeConfig.policy !== 'WORKFLOW_DRIVEN' ? (
        <p className="text-sm text-slate-600">
          Legacy workflows keep today&apos;s intake behavior. Switch to workflow-driven when you are ready to configure
          fields, documents, age, and tenure from this workflow.
        </p>
      ) : (
        <>
          <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <h3 className="text-sm font-medium text-slate-800">Personal fields</h3>
            <div className="mt-2 grid gap-3 sm:grid-cols-2">
              {(['dateOfBirth', 'gender', 'occupation', 'loanPurpose'] as const).map((field) => {
                const cfg = intakeConfig.personalFields?.[field] ?? {}
                const fieldLabel =
                  field === 'dateOfBirth'
                    ? 'Date of birth'
                    : field === 'gender'
                      ? 'Gender'
                      : field === 'occupation'
                        ? 'Occupation'
                        : 'Loan purpose'
                return (
                  <div key={field} className="rounded border border-slate-200 bg-white p-2">
                    <p className="text-xs font-medium capitalize text-slate-700">{fieldLabel}</p>
                    <label className="mt-1 flex items-center gap-2 text-xs text-slate-600">
                      <input
                        type="checkbox"
                        className="rounded border-slate-300"
                        checked={cfg.collect === true}
                        onChange={(e) =>
                          patch({
                            personalFields: {
                              ...intakeConfig.personalFields,
                              [field]: { ...cfg, collect: e.target.checked },
                            },
                          })
                        }
                      />
                      Collect at intake
                    </label>
                    <label className="mt-1 flex items-center gap-2 text-xs text-slate-600">
                      <input
                        type="checkbox"
                        className="rounded border-slate-300"
                        checked={cfg.required === true}
                        disabled={!cfg.collect}
                        onChange={(e) =>
                          patch({
                            personalFields: {
                              ...intakeConfig.personalFields,
                              [field]: { ...cfg, required: e.target.checked },
                            },
                          })
                        }
                      />
                      Required
                    </label>
                  </div>
                )
              })}
            </div>
          </section>

          <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <h3 className="text-sm font-medium text-slate-800">Age rules</h3>
            <label className="mt-2 flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                className="rounded border-slate-300"
                checked={intakeConfig.ageRules?.enabled === true}
                onChange={(e) =>
                  patch({
                    ageRules: {
                      ...intakeConfig.ageRules,
                      enabled: e.target.checked,
                      minAge: intakeConfig.ageRules?.minAge ?? 21,
                      maxAge: intakeConfig.ageRules?.maxAge ?? 65,
                    },
                  })
                }
              />
              Validate min / max age from date of birth
            </label>
            {intakeConfig.ageRules?.enabled ? (
              <div className="mt-2 flex flex-wrap gap-3">
                <label className="block text-xs text-slate-600">
                  Min age
                  <input
                    type="number"
                    className="mt-0.5 block w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                    value={intakeConfig.ageRules?.minAge ?? 21}
                    onChange={(e) =>
                      patch({
                        ageRules: {
                          ...intakeConfig.ageRules,
                          enabled: true,
                          minAge: Number.parseInt(e.target.value, 10) || 0,
                        },
                      })
                    }
                  />
                </label>
                <label className="block text-xs text-slate-600">
                  Max age
                  <input
                    type="number"
                    className="mt-0.5 block w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                    value={intakeConfig.ageRules?.maxAge ?? 65}
                    onChange={(e) =>
                      patch({
                        ageRules: {
                          ...intakeConfig.ageRules,
                          enabled: true,
                          maxAge: Number.parseInt(e.target.value, 10) || 0,
                        },
                      })
                    }
                  />
                </label>
              </div>
            ) : null}
          </section>

          <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <h3 className="text-sm font-medium text-slate-800">Tenure</h3>
            <label className="mt-2 block text-xs text-slate-600">
              Input mode
              <select
                className="mt-0.5 block w-full max-w-xs rounded border border-slate-300 bg-white px-2 py-1 text-sm"
                value={intakeConfig.tenureRules?.inputMode ?? 'numeric'}
                onChange={(e) =>
                  patch({
                    tenureRules: {
                      ...intakeConfig.tenureRules,
                      inputMode: e.target.value as 'numeric' | 'dropdown',
                    },
                  })
                }
              >
                <option value="numeric">Free numeric input (min / max)</option>
                <option value="dropdown">Dropdown of preset values</option>
              </select>
            </label>
            {intakeConfig.tenureRules?.inputMode !== 'dropdown' ? (
              <div className="mt-2 flex flex-wrap gap-3">
                <label className="block text-xs text-slate-600">
                  Min
                  <input
                    type="number"
                    className="mt-0.5 block w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                    value={intakeConfig.tenureRules?.min ?? 1}
                    onChange={(e) =>
                      patch({
                        tenureRules: {
                          ...intakeConfig.tenureRules,
                          min: Number.parseInt(e.target.value, 10) || 0,
                        },
                      })
                    }
                  />
                </label>
                <label className="block text-xs text-slate-600">
                  Max
                  <input
                    type="number"
                    className="mt-0.5 block w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                    value={intakeConfig.tenureRules?.max ?? 360}
                    onChange={(e) =>
                      patch({
                        tenureRules: {
                          ...intakeConfig.tenureRules,
                          max: Number.parseInt(e.target.value, 10) || 0,
                        },
                      })
                    }
                  />
                </label>
              </div>
            ) : (
              <div className="mt-2 space-y-2">
                {(intakeConfig.tenureRules?.options ?? []).map((opt, idx) => (
                  <div key={idx} className="flex flex-wrap items-end gap-2">
                    <label className="block text-xs text-slate-600">
                      Label
                      <input
                        className="mt-0.5 block w-40 rounded border border-slate-300 px-2 py-1 text-sm"
                        value={opt.label}
                        onChange={(e) => {
                          const options = [...(intakeConfig.tenureRules?.options ?? [])]
                          options[idx] = { ...opt, label: e.target.value }
                          patch({ tenureRules: { ...intakeConfig.tenureRules, options } })
                        }}
                      />
                    </label>
                    <label className="block text-xs text-slate-600">
                      Value
                      <input
                        className="mt-0.5 block w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                        value={opt.value}
                        onChange={(e) => {
                          const options = [...(intakeConfig.tenureRules?.options ?? [])]
                          options[idx] = { ...opt, value: e.target.value }
                          patch({ tenureRules: { ...intakeConfig.tenureRules, options } })
                        }}
                      />
                    </label>
                    <button
                      type="button"
                      className="text-xs text-rose-700"
                      onClick={() => {
                        const options = (intakeConfig.tenureRules?.options ?? []).filter((_, i) => i !== idx)
                        patch({ tenureRules: { ...intakeConfig.tenureRules, options } })
                      }}
                    >
                      Remove
                    </button>
                  </div>
                ))}
                <button
                  type="button"
                  className="rounded border border-slate-300 bg-white px-2 py-1 text-xs"
                  onClick={() =>
                    patch({
                      tenureRules: {
                        ...intakeConfig.tenureRules,
                        inputMode: 'dropdown',
                        options: [
                          ...(intakeConfig.tenureRules?.options ?? []),
                          { value: '90', label: '90 Days', unit: 'Day' },
                        ],
                      },
                    })
                  }
                >
                  Add tenure option
                </button>
              </div>
            )}
          </section>

          <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <h3 className="text-sm font-medium text-slate-800">Allowed states (optional)</h3>
            <p className="mt-1 text-xs text-slate-500">
              When any states are checked, intake state dropdowns only show those. Leave none checked to allow all
              states.
            </p>
            <AllowedStatesCheckboxSection
              selected={intakeConfig.locationRules?.allowedStates ?? []}
              onChange={(allowedStates) =>
                patch({
                  locationRules: {
                    ...intakeConfig.locationRules,
                    allowedStates,
                  },
                })
              }
            />
          </section>

          <CodedOptionsEditor
            title="Occupation options"
            description="Allowed dropdown values for occupation at intake. Scores are configured on underwriting scorecards."
            options={intakeConfig.occupationRules?.options ?? DEFAULT_OCCUPATION_OPTIONS}
            onChange={(options) => patch({ occupationRules: { options } })}
          />

          <CodedOptionsEditor
            title="Loan purpose options"
            description="Allowed dropdown values for loan purpose at intake. Scores are configured on underwriting scorecards."
            options={intakeConfig.loanPurposeRules?.options ?? DEFAULT_LOAN_PURPOSE_OPTIONS}
            onChange={(options) => patch({ loanPurposeRules: { options } })}
          />

          <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-sm font-medium text-slate-800">Mandatory OR groups</h3>
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs"
                onClick={() =>
                  patch({
                    mandatoryFieldGroups: [...(intakeConfig.mandatoryFieldGroups ?? []), newMandatoryGroup()],
                  })
                }
              >
                Add group
              </button>
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Example: Aadhaar OR Voter ID OR Driving licence — at least one must be provided.
            </p>
            {(intakeConfig.mandatoryFieldGroups ?? []).map((group, gIdx) => (
              <MandatoryGroupRow
                key={group.id}
                group={group}
                configuredSteps={configuredSteps}
                onChange={(next) => {
                  const groups = [...(intakeConfig.mandatoryFieldGroups ?? [])]
                  groups[gIdx] = next
                  patch({ mandatoryFieldGroups: groups })
                }}
                onRemove={() => {
                  const groups = (intakeConfig.mandatoryFieldGroups ?? []).filter((_, i) => i !== gIdx)
                  patch({ mandatoryFieldGroups: groups })
                }}
              />
            ))}
          </section>

          <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-sm font-medium text-slate-800">Standalone documents</h3>
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs"
                onClick={() =>
                  patch({
                    standaloneDocuments: [...(intakeConfig.standaloneDocuments ?? []), newStandaloneDocument()],
                  })
                }
              >
                Add document
              </button>
            </div>
            {(intakeConfig.standaloneDocuments ?? []).map((doc, dIdx) => (
              <StandaloneDocRow
                key={`${doc.documentType}-${dIdx}`}
                doc={doc}
                onChange={(next) => {
                  const docs = [...(intakeConfig.standaloneDocuments ?? [])]
                  docs[dIdx] = next
                  patch({ standaloneDocuments: docs })
                }}
                onRemove={() => {
                  const docs = (intakeConfig.standaloneDocuments ?? []).filter((_, i) => i !== dIdx)
                  patch({ standaloneDocuments: docs })
                }}
              />
            ))}
          </section>
          {!hideCoApplicant ? (
            <CoApplicantSection
              config={intakeConfig.coApplicant ?? {}}
              configuredSteps={configuredSteps}
              primaryIntake={intakeConfig}
              onChange={(coApplicant) =>
                patch({
                  coApplicant: {
                    ...(intakeConfig.coApplicant ?? {}),
                    ...coApplicant,
                  },
                })
              }
            />
          ) : null}
          {showContacts ? (
            <ContactsSection
              config={intakeConfig.contacts ?? {}}
              onChange={(contacts) =>
                patch({
                  contacts: {
                    ...(intakeConfig.contacts ?? {}),
                    ...contacts,
                  },
                })
              }
            />
          ) : null}
        </>
      )}
    </div>
  )
}

function ContactsSection({
  config,
  onChange,
}: {
  config: WorkflowContactsConfig
  onChange: (next: WorkflowContactsConfig) => void
}) {
  const enabled = config.enabled === true
  const maxUsers = Math.max(1, Number(config.maxUsers ?? 5) || 5)
  return (
    <section className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-medium text-slate-800">Contacts / users</h3>
          <p className="mt-1 text-xs text-slate-500">
            When enabled, anchor intake shows a Users step before review to capture portal users and signing
            authorities.
          </p>
        </div>
        <label className="flex items-center gap-2 text-xs text-slate-700">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => onChange({ ...config, enabled: e.target.checked })}
          />
          Enable users section
        </label>
      </div>
      {enabled ? (
        <label className="mt-3 block text-xs text-slate-600">
          Max users allowed
          <input
            type="number"
            min={1}
            max={50}
            className="mt-0.5 block w-28 rounded border border-slate-300 px-2 py-1 text-sm"
            value={maxUsers}
            onChange={(e) =>
              onChange({
                ...config,
                enabled: true,
                maxUsers: Math.max(1, Number.parseInt(e.target.value, 10) || 1),
              })
            }
          />
          <span className="mt-0.5 block text-[11px] text-slate-500">
            Includes the primary corporate contact. Signing authority emails are used for e-sign invitations.
          </span>
        </label>
      ) : null}
    </section>
  )
}

function CoApplicantSection({
  config,
  configuredSteps,
  primaryIntake,
  onChange,
}: {
  config: WorkflowCoApplicantConfig
  configuredSteps: string[]
  primaryIntake: WorkflowIntakeConfig
  onChange: (next: WorkflowCoApplicantConfig) => void
}) {
  const patch = (partial: Partial<WorkflowCoApplicantConfig>) => onChange({ ...config, ...partial })
  const personal = config.personalFields ?? {}
  const steps = [
    ...new Set(
      [...KYC_IDENTITY_WORKFLOW_STEPS, ...configuredSteps]
        .map((step) => (typeof step === 'string' ? step.trim() : ''))
        .filter(Boolean),
    ),
  ]
  const selectedKycSteps = Array.isArray(config.coApplicantKycSteps)
    ? config.coApplicantKycSteps.filter((step): step is string => typeof step === 'string' && step.length > 0)
    : []
  const enabled = config.enabled === true
  const selectedKycCount = selectedKycSteps.length
  const docCount = config.standaloneDocuments?.length ?? 0
  const groupCount = config.mandatoryFieldGroups?.length ?? 0

  function toggleKycStep(step: string) {
    const selected = selectedKycSteps.includes(step)
    patch({
      coApplicantKycSteps: selected
        ? selectedKycSteps.filter((value) => value !== step)
        : [...selectedKycSteps, step],
    })
  }

  const copyFromPrimary = () =>
    patch({
      personalFields: {
        dateOfBirth: { ...(primaryIntake.personalFields?.dateOfBirth ?? { collect: true, required: true }) },
        gender: { ...(primaryIntake.personalFields?.gender ?? { collect: true, required: false }) },
        occupation: { ...(primaryIntake.personalFields?.occupation ?? { collect: false, required: false }) },
      },
      ageRules: { ...(primaryIntake.ageRules ?? { enabled: false, minAge: 18, maxAge: 70 }) },
      coApplicantKycSteps: [...(configuredSteps.length ? configuredSteps : KYC_IDENTITY_WORKFLOW_STEPS)],
      mandatoryFieldGroups: (primaryIntake.mandatoryFieldGroups ?? []).map((group) => ({
        ...group,
        steps: [...group.steps],
      })),
      standaloneDocuments: (primaryIntake.standaloneDocuments ?? []).map((doc) => ({ ...doc })),
    })

  const updatePersonal = (key: 'dateOfBirth' | 'gender' | 'occupation', collect: boolean) =>
    patch({
      personalFields: {
        ...personal,
        [key]: {
          ...(personal[key] ?? {}),
          collect,
          required: collect && personal[key]?.required === true,
        },
      },
    })

  const personalFieldCards: {
    key: 'dateOfBirth' | 'gender' | 'occupation'
    label: string
    hint: string
  }[] = [
    { key: 'dateOfBirth', label: 'Date of birth', hint: 'Collect DOB on co-applicant intake' },
    { key: 'gender', label: 'Gender', hint: 'Show gender on co-applicant forms' },
    { key: 'occupation', label: 'Occupation', hint: 'Show occupation dropdown for co-applicants' },
  ]

  return (
    <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 bg-gradient-to-r from-slate-50 via-white to-slate-50 px-4 py-3.5">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-sm font-semibold text-slate-900">Co-applicants</h3>
            <span
              className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium ${
                enabled
                  ? 'bg-emerald-50 text-emerald-800 ring-1 ring-inset ring-emerald-200'
                  : 'bg-slate-100 text-slate-600 ring-1 ring-inset ring-slate-200'
              }`}
            >
              {enabled ? 'Enabled' : 'Disabled'}
            </span>
          </div>
          <p className="mt-1 max-w-2xl text-xs leading-relaxed text-slate-500">
            Primary applicants use the intake rules above. When enabled, co-applicants get their own personal-field,
            KYC, and document settings for joint applications.
          </p>
          {enabled ? (
            <p className="mt-2 text-[11px] text-slate-500">
              Limits {config.minCoApplicants ?? 0}–{config.maxCoApplicants ?? 3}
              {' · '}
              {selectedKycCount} KYC step{selectedKycCount === 1 ? '' : 's'}
              {' · '}
              {groupCount} any-one group{groupCount === 1 ? '' : 's'}
              {' · '}
              {docCount} document{docCount === 1 ? '' : 's'}
            </p>
          ) : null}
        </div>
        <label className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm">
          <input
            type="checkbox"
            className="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-400"
            checked={enabled}
            onChange={(e) => patch({ enabled: e.target.checked })}
          />
          <span className="font-medium">Enable co-applicants</span>
        </label>
      </div>

      {enabled ? (
        <div className="space-y-4 p-4">
          <div className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Applicant limits</h4>
                <p className="mt-0.5 text-xs text-slate-500">Minimum and maximum co-applicants allowed on create.</p>
              </div>
              <button
                type="button"
                className="rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-800 shadow-sm hover:bg-slate-50"
                onClick={copyFromPrimary}
              >
                Copy rules from primary
              </button>
            </div>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <label className="block rounded-md border border-slate-200 bg-white px-3 py-2">
                <span className="text-[11px] font-medium uppercase tracking-wide text-slate-500">Minimum</span>
                <input
                  className="mt-1 block w-full rounded border border-slate-300 px-2 py-1.5 text-sm tabular-nums"
                  type="number"
                  min={0}
                  value={config.minCoApplicants ?? 0}
                  onChange={(e) => patch({ minCoApplicants: Number(e.target.value) || 0 })}
                />
              </label>
              <label className="block rounded-md border border-slate-200 bg-white px-3 py-2">
                <span className="text-[11px] font-medium uppercase tracking-wide text-slate-500">Maximum</span>
                <input
                  className="mt-1 block w-full rounded border border-slate-300 px-2 py-1.5 text-sm tabular-nums"
                  type="number"
                  min={0}
                  value={config.maxCoApplicants ?? 3}
                  onChange={(e) => patch({ maxCoApplicants: Number(e.target.value) || 0 })}
                />
              </label>
            </div>
          </div>

          <div className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Personal fields to collect</h4>
            <p className="mt-0.5 text-xs text-slate-500">
              Choose which fields appear on co-applicant intake (portal invite or staff fill).
            </p>
            <div className="mt-3 grid gap-2 sm:grid-cols-3">
              {personalFieldCards.map((field) => {
                const checked = personal[field.key]?.collect !== false
                return (
                  <label
                    key={field.key}
                    className={`flex cursor-pointer flex-col gap-1 rounded-lg border px-3 py-2.5 transition ${
                      checked
                        ? 'border-slate-300 bg-white shadow-sm ring-1 ring-slate-200'
                        : 'border-dashed border-slate-200 bg-white/70'
                    }`}
                  >
                    <span className="flex items-center gap-2 text-sm font-medium text-slate-800">
                      <input
                        type="checkbox"
                        className="h-3.5 w-3.5 rounded border-slate-300 text-slate-900 focus:ring-slate-400"
                        checked={checked}
                        onChange={(e) => updatePersonal(field.key, e.target.checked)}
                      />
                      {field.label}
                    </span>
                    <span className="pl-5 text-[11px] leading-snug text-slate-500">{field.hint}</span>
                  </label>
                )
              })}
            </div>
          </div>

          <div className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Co-applicant KYC steps</h4>
            <p className="mt-0.5 text-xs text-slate-500">
              Identity checks run for each co-applicant when KYC is executed. Leave empty to use all identity steps.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              {steps.map((step) => {
                const selected = selectedKycSteps.includes(step)
                return (
                  <button
                    key={step}
                    type="button"
                    aria-pressed={selected}
                    className={`inline-flex items-center rounded-md border px-2.5 py-1.5 text-xs font-medium transition ${
                      selected
                        ? 'border-slate-800 bg-slate-900 text-white'
                        : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                    }`}
                    onClick={() => toggleKycStep(step)}
                  >
                    {step.replace(/_/g, ' ')}
                  </button>
                )
              })}
            </div>
          </div>

          <div className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Co-applicant KYC groups (any-one)
                </h4>
                <p className="mt-0.5 text-xs text-slate-500">
                  Optional groups where any one selected step can satisfy the requirement.
                </p>
              </div>
              <button
                type="button"
                className="rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-800 shadow-sm hover:bg-slate-50"
                onClick={() =>
                  patch({
                    mandatoryFieldGroups: [...(config.mandatoryFieldGroups ?? []), newMandatoryGroup()],
                  })
                }
              >
                Add group
              </button>
            </div>
            {(config.mandatoryFieldGroups ?? []).length === 0 ? (
              <p className="mt-3 rounded-md border border-dashed border-slate-200 bg-white/80 px-3 py-3 text-xs text-slate-500">
                No any-one groups configured for co-applicants.
              </p>
            ) : (
              <div className="mt-2 space-y-2">
                {(config.mandatoryFieldGroups ?? []).map((group, index) => (
                  <MandatoryGroupRow
                    key={`co-group-${index}`}
                    group={group}
                    configuredSteps={steps}
                    onChange={(next) => {
                      const groups = [...(config.mandatoryFieldGroups ?? [])]
                      groups[index] = next
                      patch({ mandatoryFieldGroups: groups })
                    }}
                    onRemove={() =>
                      patch({
                        mandatoryFieldGroups: (config.mandatoryFieldGroups ?? []).filter((_, i) => i !== index),
                      })
                    }
                  />
                ))}
              </div>
            )}
          </div>

          <div className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Co-applicant documents</h4>
                <p className="mt-0.5 text-xs text-slate-500">
                  Document checklist shown on co-applicant portal / staff-fill intake.
                </p>
              </div>
              <button
                type="button"
                className="rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-800 shadow-sm hover:bg-slate-50"
                onClick={() =>
                  patch({
                    standaloneDocuments: [...(config.standaloneDocuments ?? []), newStandaloneDocument()],
                  })
                }
              >
                Add document
              </button>
            </div>
            {(config.standaloneDocuments ?? []).length === 0 ? (
              <p className="mt-3 rounded-md border border-dashed border-slate-200 bg-white/80 px-3 py-3 text-xs text-slate-500">
                No co-applicant documents configured yet.
              </p>
            ) : (
              <div className="mt-2 space-y-2">
                {(config.standaloneDocuments ?? []).map((doc, index) => (
                  <StandaloneDocRow
                    key={`${doc.documentType}-${index}`}
                    doc={doc}
                    onChange={(next) => {
                      const documents = [...(config.standaloneDocuments ?? [])]
                      documents[index] = next
                      patch({ standaloneDocuments: documents })
                    }}
                    onRemove={() =>
                      patch({
                        standaloneDocuments: (config.standaloneDocuments ?? []).filter((_, i) => i !== index),
                      })
                    }
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="px-4 py-5">
          <p className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
            Co-applicants are turned off for this workflow. Enable to configure limits, personal fields, KYC steps, and
            documents for joint applicants.
          </p>
        </div>
      )}
    </section>
  )
}

function MandatoryGroupRow({
  group,
  configuredSteps,
  onChange,
  onRemove,
}: {
  group: WorkflowMandatoryFieldGroup
  configuredSteps: string[]
  onChange: (g: WorkflowMandatoryFieldGroup) => void
  onRemove: () => void
}) {
  const pool = [...new Set([...configuredSteps, ...group.steps])].filter(Boolean)
  return (
    <div className="mt-2 rounded border border-slate-200 bg-white p-2">
      <div className="flex flex-wrap items-center gap-2">
        <input
          className="min-w-0 flex-1 rounded border border-slate-300 px-2 py-1 text-sm"
          placeholder="Group label (e.g. Government ID — any one)"
          value={group.label}
          onChange={(e) => onChange({ ...group, label: e.target.value })}
        />
        <button type="button" className="text-xs text-rose-700" onClick={onRemove}>
          Remove
        </button>
      </div>
      <div className="mt-2 flex flex-wrap gap-2">
        {pool.map((step) => {
          const checked = group.steps.includes(step)
          if (!KYC_IDENTITY_WORKFLOW_STEPS.includes(step as (typeof KYC_IDENTITY_WORKFLOW_STEPS)[number])) {
            return null
          }
          return (
            <label key={step} className="flex items-center gap-1 rounded border border-slate-200 bg-slate-50 px-2 py-1 text-xs">
              <input
                type="checkbox"
                className="rounded border-slate-300"
                checked={checked}
                onChange={(e) => {
                  const steps = e.target.checked
                    ? [...group.steps, step]
                    : group.steps.filter((s) => s !== step)
                  onChange({ ...group, steps })
                }}
              />
              {step}
            </label>
          )
        })}
      </div>
    </div>
  )
}

function StandaloneDocRow({
  doc,
  onChange,
  onRemove,
}: {
  doc: WorkflowStandaloneDocument
  onChange: (d: WorkflowStandaloneDocument) => void
  onRemove: () => void
}) {
  return (
    <div className="mt-2 flex flex-wrap items-center gap-2 rounded border border-slate-200 bg-white p-2">
      <input
        className="w-36 rounded border border-slate-300 px-2 py-1 text-sm"
        value={doc.documentType}
        onChange={(e) => onChange({ ...doc, documentType: e.target.value.toUpperCase() })}
      />
      <input
        className="min-w-0 flex-1 rounded border border-slate-300 px-2 py-1 text-sm"
        placeholder="Label"
        value={doc.label ?? ''}
        onChange={(e) => onChange({ ...doc, label: e.target.value })}
      />
      <label className="flex items-center gap-1 text-xs text-slate-600">
        <input
          type="checkbox"
          className="rounded border-slate-300"
          checked={doc.required === true}
          onChange={(e) => onChange({ ...doc, required: e.target.checked })}
        />
        Required
      </label>
      <button type="button" className="text-xs text-rose-700" onClick={onRemove}>
        Remove
      </button>
    </div>
  )
}
