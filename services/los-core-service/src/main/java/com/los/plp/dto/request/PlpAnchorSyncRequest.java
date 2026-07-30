package com.los.plp.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlpAnchorSyncRequest {
    private String sourceSystem;
    private String losAnchorId;
    private AnchorPayload anchor;
    /** When true, PLP provisions portal user and returns temporaryPassword if newly created. */
    private Boolean provisionAtNotify;
    /** INVITED | IN_PROGRESS | SUBMITTED | SENT_BACK | COMPLETED */
    private String onboardingStatus;
    /** LOS loan application id for portal onboarding linkage. */
    private String losApplicationId;

    @Data
    @Builder
    public static class AnchorPayload {
        private String name;
        private String code;
        private String pan;
        private String gstin;
        private String email;
        private String mobile;
        private String address;
    }
}
