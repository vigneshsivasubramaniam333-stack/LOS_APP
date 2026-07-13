import { Link, useParams } from 'react-router-dom'
import { ApplicationIntakeWizard } from '@/components/intake/ApplicationIntakeWizard'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { isUuid } from '@/lib/format'

/** Staff continues intake on an existing DRAFT application. */
export function ApplicationIntakeEditPage() {
  const { id } = useParams<{ id: string }>()
  if (!id || !isUuid(id)) {
    return (
      <div>
        <PageHeader title="Continue application" />
        <ErrorState message="Invalid application id." />
        <p className="mt-4 text-sm">
          <Link to="/applications" className="text-slate-800 underline">
            Back to applications
          </Link>
        </p>
      </div>
    )
  }
  return (
    <ApplicationIntakeWizard mode="ADMIN_INTERNAL" variant="staff" editApplicationId={id} />
  )
}
