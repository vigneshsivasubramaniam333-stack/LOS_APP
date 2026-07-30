package com.los.core.model.dto.response;

import com.los.core.model.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class BorrowerApplicationSummaryResponse {
    UUID applicationId;
    String applicationNumber;
    String product;
    String friendlyStatus;
    ApplicationStatus status;
    Instant createdAt;
    Instant updatedAt;
    /** PRIMARY / CO_APPLICANT when the viewer is linked via application_parties; null for legacy rows. */
    String partyRole;
    /** Party id for co-applicant resume links (`?partyId=`). */
    UUID partyId;
    String partyIntakeStatus;
    Boolean canResumeMyIntake;
    Integer pendingCoApplicantCount;
    /** Viewer-specific status message when the application is awaiting another applicant. */
    String viewerFriendlyStatus;
}
