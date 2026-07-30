package com.los.core.model.dto.response;

import com.los.core.model.enums.ApplicationPartyRole;
import com.los.core.model.enums.PartyEsignStatus;
import com.los.core.model.enums.PartyIntakeStatus;
import com.los.core.model.enums.PartyKycStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ApplicationPartyResponse {
    private UUID id;
    private UUID applicationId;
    private ApplicationPartyRole role;
    private int sequenceNo;
    private UUID userId;
    private Map<String, Object> personalInfo;
    private PartyIntakeStatus intakeStatus;
    private PartyKycStatus kycStatus;
    private PartyEsignStatus esignStatus;
    private boolean requiredForDisbursement;
    private Instant createdAt;
    private Instant updatedAt;
    private String displayName;
    private String email;
    private String mobile;
}
