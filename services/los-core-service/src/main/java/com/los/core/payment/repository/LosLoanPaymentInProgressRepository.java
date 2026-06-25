package com.los.core.payment.repository;

import com.los.core.payment.model.LosLoanPaymentInProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LosLoanPaymentInProgressRepository extends JpaRepository<LosLoanPaymentInProgress, UUID> {

    List<LosLoanPaymentInProgress> findByPipStatusOrderByCreatedAtDesc(String pipStatus);
}
