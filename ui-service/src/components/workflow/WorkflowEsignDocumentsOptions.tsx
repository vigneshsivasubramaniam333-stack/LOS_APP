import {
  ESIGN_ADDITIONAL_DOCUMENT_CATALOG,
  type EsignAdditionalDocument,
  type EsignDocumentsUiConfig,
  type VisualWorkflowStep,
} from '@/lib/workflowVisual'

function emptyConfig(): EsignDocumentsUiConfig {
  return { defaultDocumentKey: 'KFS_AGREEMENT', additional: [] }
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
          initiate and signed as separate links. Leave additional empty for single-document (borrower) behavior.
        </p>
        <label className="block text-xs text-slate-600">
          Default document key
          <input
            className="mt-1 w-full rounded border border-slate-300 px-2 py-1"
            value={cfg.defaultDocumentKey}
            onChange={(e) => write({ ...cfg, defaultDocumentKey: e.target.value.trim() || 'KFS_AGREEMENT' })}
          />
        </label>
        {cfg.additional.map((doc, idx) => (
          <div key={`${doc.documentType}-${idx}`} className="flex flex-wrap items-center gap-2 rounded border border-slate-100 p-2">
            <select
              className="rounded border border-slate-300 px-2 py-1 text-xs"
              value={doc.documentType}
              onChange={(e) => {
                const additional = cfg.additional.slice()
                const type = e.target.value
                const catalog = ESIGN_ADDITIONAL_DOCUMENT_CATALOG.find((c) => c.documentType === type)
                additional[idx] = {
                  ...doc,
                  documentType: type,
                  label: catalog?.label ?? type,
                }
                updateAdditional(additional)
              }}
            >
              {ESIGN_ADDITIONAL_DOCUMENT_CATALOG.map((t) => (
                <option key={t.documentType} value={t.documentType}>
                  {t.label}
                </option>
              ))}
              {!ESIGN_ADDITIONAL_DOCUMENT_CATALOG.some((c) => c.documentType === doc.documentType) ? (
                <option value={doc.documentType}>{doc.documentType}</option>
              ) : null}
            </select>
            <input
              className="min-w-[8rem] flex-1 rounded border border-slate-300 px-2 py-1 text-xs"
              value={doc.label}
              onChange={(e) => {
                const additional = cfg.additional.slice()
                additional[idx] = { ...doc, label: e.target.value }
                updateAdditional(additional)
              }}
            />
            <label className="flex items-center gap-1 text-xs text-slate-600">
              <input
                type="checkbox"
                checked={doc.required}
                onChange={(e) => {
                  const additional = cfg.additional.slice()
                  additional[idx] = { ...doc, required: e.target.checked }
                  updateAdditional(additional)
                }}
              />
              Required
            </label>
            <button
              type="button"
              className="text-xs text-red-600"
              onClick={() => updateAdditional(cfg.additional.filter((_, i) => i !== idx))}
            >
              Remove
            </button>
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
