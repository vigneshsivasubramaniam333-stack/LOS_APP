package com.los.core.model.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class UpsertApplicationPartiesRequest {

    /** Full replacement list of co-applicants (PRIMARY is managed separately / always ensured). */
    private List<CoApplicantDraft> coApplicants;

    @Data
    public static class CoApplicantDraft {
        /** Existing party id when updating; null for new. */
        private UUID id;
        private Map<String, Object> personalInfo;
        private Boolean requiredForDisbursement;
    }
}
