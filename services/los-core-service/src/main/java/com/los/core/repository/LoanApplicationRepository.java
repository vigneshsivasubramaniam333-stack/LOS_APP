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
}
