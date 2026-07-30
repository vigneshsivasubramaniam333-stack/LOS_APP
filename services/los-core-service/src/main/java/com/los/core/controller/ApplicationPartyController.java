package com.los.core.controller;

import com.los.core.model.dto.request.UpsertApplicationPartiesRequest;
import com.los.core.model.dto.response.ApplicationPartyResponse;
import com.los.core.model.entity.ApplicationParty;
import com.los.core.service.borrower.BorrowerIntakeDelegationService;
import com.los.core.service.borrower.BorrowerApplicationOwnershipService;
import com.los.core.service.loan.ApplicationPartyService;
import com.los.core.service.loan.WorkflowRoleGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/parties")
@RequiredArgsConstructor
@Tag(name = "Application Parties", description = "Primary + co-applicant management")
public class ApplicationPartyController {

    private final ApplicationPartyService applicationPartyService;
    private final BorrowerIntakeDelegationService borrowerIntakeDelegationService;
    private final BorrowerApplicationOwnershipService ownershipService;
    private final WorkflowRoleGuard workflowRoleGuard;

    @GetMapping
    @Operation(summary = "List applicants (PRIMARY + co-applicants)")
    public ResponseEntity<List<ApplicationPartyResponse>> list(
            @PathVariable UUID applicationId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        if (isBorrower(userRole)) {
            UUID uid = requireUserId(userId);
            if (!ownershipService.ownsApplication(uid, applicationId)) {
                throw new com.los.core.exception.BusinessRuleException("You can only view your own applicants");
            }
        }
        return ResponseEntity.ok(applicationPartyService.listParties(applicationId));
    }

    @PutMapping
    @Operation(summary = "Replace co-applicant list (PRIMARY is always retained)")
    public ResponseEntity<List<ApplicationPartyResponse>> upsert(
            @PathVariable UUID applicationId,
            @Valid @RequestBody UpsertApplicationPartiesRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        workflowRoleGuard.requireIntakeRole(userRole);
        return ResponseEntity.ok(applicationPartyService.upsertCoApplicants(applicationId, request));
    }

    @PostMapping("/{partyId}/submit")
    @Operation(summary = "Co-applicant submits their intake portion")
    public ResponseEntity<ApplicationPartyResponse> submitParty(
            @PathVariable UUID applicationId,
            @PathVariable UUID partyId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID uid = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(borrowerIntakeDelegationService.submitCoApplicantIntake(applicationId, partyId, uid));
    }

    @PutMapping("/{partyId}/personal-info")
    @Operation(summary = "Update party personal info (co-applicant portal)")
    public ResponseEntity<ApplicationPartyResponse> updatePersonalInfo(
            @PathVariable UUID applicationId,
            @PathVariable UUID partyId,
            @RequestBody Map<String, Object> personalInfo,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        if (isBorrower(userRole)) {
            UUID uid = requireUserId(userId);
            ApplicationParty party = applicationPartyService.requireParty(applicationId, partyId);
            if (!uid.equals(party.getUserId())) {
                throw new com.los.core.exception.BusinessRuleException("You can only update your own applicant profile");
            }
        } else {
            workflowRoleGuard.requireIntakeRole(userRole);
        }
        var party = applicationPartyService.updatePartyPersonalInfo(applicationId, partyId, personalInfo);
        return ResponseEntity.ok(applicationPartyService.toResponse(party));
    }

    private static boolean isBorrower(String userRole) {
        return userRole != null && "BORROWER".equalsIgnoreCase(userRole.trim());
    }

    private static UUID requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new com.los.core.exception.BusinessRuleException("Borrower user id is required");
        }
        return UUID.fromString(userId);
    }
}
