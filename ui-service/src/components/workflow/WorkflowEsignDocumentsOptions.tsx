import {
  ESIGN_ADDITIONAL_DOCUMENT_CATALOG,
  type EsignAdditionalDocument,
  type EsignDocumentsUiConfig,
  type VisualWorkflowStep,
} from '@/lib/workflowVisual'

function emptyConfig(): EsignDocumentsUiConfig {
  return { defaultDocumentKey: 'KFS_AGREEMENT', expectedPageCount: 2, additional: [] }
}

export function WorkflowEsignDocumentsOptions({
  step,
  onChange,
}: {
  step: VisualWorkflowStep
  onChange: (next: VisualWorkflowStep) => void
}) {
  if (step.step !== 'ESIGN_AGREEMENT' && step.step !== 'ESIGN_KFS') {
    return null
  }
  const cfg = step.esignDocuments ?? emptyConfig()

  function write(next: EsignDocumentsUiConfig | null) {
    onChange({ ...step, esignDocuments: next })
  }

  function updateAdditional(additional: EsignAdditionalDocument[]) {
    write({ ...cfg, additional })
  }

  return (
    <details className="min-w-0 rounded border border-slate-200 bg-white p-2 sm:col-span-2" open>
      <summary className="cursor-pointer text-xs font-medium text-slate-600">
        eSign documents ({cfg.additional.length} additional)
      </summary>
      <div className="mt-2 space-y-2 border-t border-slate-100 pt-2">
        <p className="text-xs text-slate-500">
          Default program terms / KFS is always signed. Add extra document types that must be uploaded before
          initiate and signed as separate links (each email gets its own signing URL). Leave additional empty for
          single-document behavior.
        </p>
        <label className="block text-xs text-slate-600">
          Default document key
          <input
            className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
            value={cfg.defaultDocumentKey}
            onChange={(e) => write({ ...cfg, defaultDocumentKey: e.target.value.trim() || 'KFS_AGREEMENT' })}
          />
        </label>
        <label className="block text-xs text-slate-600">
          Expected page count (default document / fallback)
          <input
            type="number"
            min={1}
            className="mt-1 w-24 rounded border border-slate-300 px-2 py-1"
            value={cfg.expectedPageCount ?? 2}
            onChange={(e) => {
              const n = Number(e.target.value)
              write({ ...cfg, expectedPageCount: Number.isFinite(n) && n > 0 ? Math.floor(n) : 2 })
            }}
          />
        </label>
        {cfg.additional.map((doc, idx) => (
          <div
            key={`${doc.documentType}-${idx}`}
            className="space-y-2 rounded border border-slate-100 p-2"
          >
            <div className="flex flex-wrap items-center gap-2">
              <select
                className="rounded border border-slate-300 px-2 py-1 text-xs"
                value={
                  ESIGN_ADDITIONAL_DOCUMENT_CATALOG.some((c) => c.documentType === doc.documentType)
                    ? doc.documentType
                    : '__CUSTOM__'
                }
                onChange={(e) => {
                  const additional = cfg.additional.slice()
                  const type = e.target.value
                  if (type === '__CUSTOM__') {
                    additional[idx] = {
                      ...doc,
                      documentType: doc.documentType || 'CUSTOM_DOC',
                      label: doc.label || 'Custom document',
                    }
                  } else {
                    const catalog = ESIGN_ADDITIONAL_DOCUMENT_CATALOG.find((c) => c.documentType === type)
                    additional[idx] = {
                      ...doc,
                      documentType: type,
                      label: catalog?.label ?? type,
                    }
                  }
                  updateAdditional(additional)
                }}
              >
                {ESIGN_ADDITIONAL_DOCUMENT_CATALOG.map((t) => (
                  <option key={t.documentType} value={t.documentType}>
                    {t.label}
                  </option>
                ))}
                <option value="__CUSTOM__">Custom document type…</option>
              </select>
              <input
                className="min-w-[8rem] flex-1 rounded border border-slate-300 px-2 py-1 text-xs"
                value={doc.label}
                placeholder="Label"
                onChange={(e) => {
                  const additional = cfg.additional.slice()
                  additional[idx] = { ...doc, label: e.target.value }
                  updateAdditional(additional)
                }}
              />
              <input
                className="w-36 rounded border border-slate-300 px-2 py-1 text-xs font-mono uppercase"
                value={doc.documentType}
                placeholder="DOCUMENT_TYPE"
                onChange={(e) => {
                  const additional = cfg.additional.slice()
                  additional[idx] = {
                    ...doc,
                    documentType: e.target.value.trim().toUpperCase().replace(/\s+/g, '_'),
                  }
                  updateAdditional(additional)
                }}
              />
            </div>
            <div className="flex flex-wrap items-center gap-3 text-xs text-slate-600">
              <label className="flex items-center gap-1">
                <input
                  type="checkbox"
                  checked={doc.required}
                  onChange={(e) => {
                    const additional = cfg.additional.slice()
                    const required = e.target.checked
                    additional[idx] = {
                      ...doc,
                      required,
                      collectAtIntake: required ? doc.collectAtIntake : false,
                    }
                    updateAdditional(additional)
                  }}
                />
                Required for signing
              </label>
              <label className="flex items-center gap-1">
                <input
                  type="checkbox"
                  checked={doc.collectAtIntake}
                  disabled={!doc.required}
                  onChange={(e) => {
                    const additional = cfg.additional.slice()
                    additional[idx] = { ...doc, collectAtIntake: e.target.checked }
                    updateAdditional(additional)
                  }}
                />
                Collect at intake
              </label>
              <label className="flex items-center gap-1">
                Expected pages
                <input
                  type="number"
                  min={1}
                  className="w-14 rounded border border-slate-300 px-1 py-0.5"
                  value={doc.expectedPageCount ?? 2}
                  onChange={(e) => {
                    const n = Number(e.target.value)
                    const additional = cfg.additional.slice()
                    additional[idx] = {
                      ...doc,
                      expectedPageCount: Number.isFinite(n) && n > 0 ? Math.floor(n) : 2,
                    }
                    updateAdditional(additional)
                  }}
                />
              </label>
              <button
                type="button"
                className="text-xs text-red-600"
                onClick={() => updateAdditional(cfg.additional.filter((_, i) => i !== idx))}
              >
                Remove
              </button>
            </div>
          </div>
        ))}
        <button
          type="button"
          className="rounded border border-slate-300 bg-slate-50 px-2 py-1 text-xs"
          onClick={() => {
            const first = ESIGN_ADDITIONAL_DOCUMENT_CATALOG[0]
            updateAdditional([
              ...cfg.additional,
              {
                documentType: first?.documentType ?? 'BOARD_RESOLUTION',
                label: first?.label ?? 'Board resolution',
                required: true,
                collectAtIntake: true,
                expectedPageCount: cfg.expectedPageCount ?? 2,
              },
            ])
          }}
        >
          + Additional signing document
        </button>
      </div>
    </details>
  )
}
