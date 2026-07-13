import { ApplicationTable } from '@/components/ApplicationTable'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { useApplications } from '@/hooks/useApplications'

export function BorrowerSubmittedQueuePage() {
  const { data, loading, error } = useApplications({ status: 'BORROWER_SUBMITTED', page: 0, size: 50 })

  return (
    <div>
      <PageHeader
        title="Borrower submissions"
        description="Applications the borrower completed after staff saved a draft. Accept to start KYC or send back with notes."
      />
      {loading && <LoadingState label="Loading queue…" />}
      {error && <ErrorState message={error} />}
      {data && !loading && (
        <ApplicationTable
          rows={data.content}
          emptyMessage="No borrower-submitted applications awaiting review."
        />
      )}
    </div>
  )
}
