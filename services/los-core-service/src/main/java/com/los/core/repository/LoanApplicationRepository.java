package com.los.core.repository;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    Optional<LoanApplication> findByApplicationNumber(String applicationNumber);

    Page<LoanApplication> findByStatus(ApplicationStatus status, Pageable pageable);

    Page<LoanApplication> findByBorrowerType(BorrowerType borrowerType, Pageable pageable);

    Page<LoanApplication> findByStatusAndBorrowerType(ApplicationStatus status, BorrowerType borrowerType, Pageable pageable);

    Page<LoanApplication> findByIntakeSegment(IntakeSegment intakeSegment, Pageable pageable);

    Page<LoanApplication> findByStatusAndIntakeSegment(ApplicationStatus status, IntakeSegment intakeSegment, Pageable pageable);

    Page<LoanApplication> findByBorrowerTypeAndIntakeSegment(BorrowerType borrowerType, IntakeSegment intakeSegment, Pageable pageable);

    Page<LoanApplication> findByStatusAndBorrowerTypeAndIntakeSegment(
            ApplicationStatus status, BorrowerType borrowerType, IntakeSegment intakeSegment, Pageable pageable);

    Page<LoanApplication> findByCustomerId(UUID customerId, Pageable pageable);

    Optional<LoanApplication> findFirstByCustomerIdOrderByUpdatedAtDesc(UUID customerId);

    /**
     * Most recently updated application for this borrower that has been synced to PLP and carries a PLP
     * borrower id. Used to map a LOS borrower to their PLP borrower identity for invoice discounting.
     */
    Optional<LoanApplication> findFirstByCustomerIdAndPlpBorrowerIdIsNotNullOrderByUpdatedAtDesc(UUID customerId);

    Optional<LoanApplication> findTopByVkycTransactionId(String vkycTransactionId);

    @Query("SELECT a.status, COUNT(a) FROM LoanApplication a GROUP BY a.status")
    java.util.List<Object[]> countByStatusGrouped();

    long countByStatus(ApplicationStatus status);

    long countByStatusAndCreatedAtBefore(ApplicationStatus status, java.time.Instant before);

    java.util.List<LoanApplication> findByEscalatedFalseAndSlaDeadlineBefore(java.time.Instant deadline);

    java.util.List<LoanApplication> findByStatusIn(java.util.List<ApplicationStatus> statuses);

    long countBySubProgramId(UUID subProgramId);

    java.util.List<LoanApplication> findBySubProgramId(UUID subProgramId);

    java.util.List<LoanApplication> findBySubProgramIdIn(java.util.Collection<UUID> subProgramIds);

    // ----- Duplicate-identity detection at submission (email / PAN / GSTN) -----
    // JSONB lookups are case-insensitive and ignore the application being submitted. They return
    // candidate matches so the service layer can decide whether the match is the same customer
    // (allowed) or a different borrower (blocked).

    @Query(value = """
            SELECT * FROM loan_applications a
            WHERE a.id <> :selfId
              AND lower(coalesce(a.personal_info->>'email', a.personal_info->>'borrowerEmail',
                                 a.personal_info->>'contactEmail', a.business_info->>'email')) = lower(:email)
            """, nativeQuery = true)
    java.util.List<LoanApplication> findOthersByEmail(@org.springframework.data.repository.query.Param("selfId") UUID selfId,
                                                      @org.springframework.data.repository.query.Param("email") String email);

    @Query(value = """
            SELECT * FROM loan_applications a
            WHERE a.id <> :selfId
              AND upper(coalesce(a.personal_info->>'panNumber', a.business_info->>'entityPan')) = upper(:pan)
            """, nativeQuery = true)
    java.util.List<LoanApplication> findOthersByPan(@org.springframework.data.repository.query.Param("selfId") UUID selfId,
                                                    @org.springframework.data.repository.query.Param("pan") String pan);

    @Query(value = """
            SELECT * FROM loan_applications a
            WHERE a.id <> :selfId
              AND upper(coalesce(a.personal_info->>'gstin', a.business_info->>'gstin')) = upper(:gstin)
            """, nativeQuery = true)
    java.util.List<LoanApplication> findOthersByGstin(@org.springframework.data.repository.query.Param("selfId") UUID selfId,
                                                      @org.springframework.data.repository.query.Param("gstin") String gstin);
}
