package com.los.core.model.dto.request;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Optional notes for application review actions (handoff / send-back / accept). */
@Data
public class ReviewNotesRequest {

    private String notes;

    /** Alias accepted by CAM send-back and similar endpoints. */
    private String remarks;

    /** Applicant-party IDs to send back when {@link #sendBackMode} is SELECTED. */
    private List<UUID> partyIds;

    /** ALL (default) or SELECTED. */
    private String sendBackMode;
}
