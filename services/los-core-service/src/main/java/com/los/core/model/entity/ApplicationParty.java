package com.los.core.model.entity;

import com.los.core.model.enums.ApplicationPartyRole;
import com.los.core.model.enums.PartyEsignStatus;
import com.los.core.model.enums.PartyIntakeStatus;
import com.los.core.model.enums.PartyKycStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "application_parties", indexes = {
        @Index(name = "idx_application_parties_app", columnList = "application_id"),
        @Index(name = "idx_application_parties_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationParty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationPartyRole role;

    @Column(name = "sequence_no", nullable = false)
    @Builder.Default
    private int sequenceNo = 0;

    @Column(name = "user_id")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "personal_info", columnDefinition = "jsonb")
    private Map<String, Object> personalInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "intake_status", nullable = false, length = 30)
    @Builder.Default
    private PartyIntakeStatus intakeStatus = PartyIntakeStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 30)
    @Builder.Default
    private PartyKycStatus kycStatus = PartyKycStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "esign_status", nullable = false, length = 30)
    @Builder.Default
    private PartyEsignStatus esignStatus = PartyEsignStatus.NOT_STARTED;

    @Column(name = "required_for_disbursement", nullable = false)
    @Builder.Default
    private boolean requiredForDisbursement = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
