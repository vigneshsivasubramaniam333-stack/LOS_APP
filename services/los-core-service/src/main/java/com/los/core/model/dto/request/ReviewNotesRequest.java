package com.los.core.model.dto.request;

import lombok.Data;

/** Optional notes for application review actions (handoff / send-back / accept). */
@Data
public class ReviewNotesRequest {

    private String notes;
}
