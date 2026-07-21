import type { WorkflowCodedOption, WorkflowIntakeConfig, WorkflowMandatoryFieldGroup, WorkflowStandaloneDocument } from '@/types/workflow'
import type { VisualWorkflowStep } from '@/lib/workflowVisual'
import { KYC_IDENTITY_WORKFLOW_STEPS } from '@/lib/workflowVisual'
import { DEFAULT_LOAN_PURPOSE_OPTIONS, DEFAULT_OCCUPATION_OPTIONS } from '@/lib/intake/intakeOptionCatalogs'
import { defaultWorkflowDrivenIntakeConfig, newMandatoryGroup, newStandaloneDocument } from '@/lib/workflow/workflowIntakeRules'

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
  if ((config.mandatoryFieldGroups?.length ?? 0) > 0) {
    parts.push(`${config.mandatoryFieldGroups?.length} OR group(s)`)
  }
  return parts.join(' · ')
}

export function WorkflowIntakeRulesPanel({
  intakeConfig,
  onChange,
  visualSteps,
}: {
  intakeConfig: WorkflowIntakeConfig
  onChange: (next: WorkflowIntakeConfig) => void
  visualSteps: VisualWorkflowStep[]
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
        </>
      )}
    </div>
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
