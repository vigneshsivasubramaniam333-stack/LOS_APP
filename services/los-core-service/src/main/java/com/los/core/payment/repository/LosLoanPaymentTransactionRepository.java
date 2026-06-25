package com.los.core.payment.repository;

import com.los.core.payment.model.LosLoanPaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LosLoanPaymentTransactionRepository extends JpaRepository<LosLoanPaymentTransaction, UUID> {

    Optional<LosLoanPaymentTransaction> findByPgTransactionRef(String pgTransactionRef);
}
