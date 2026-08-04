package com.los.plp.dto.request;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
public class PlpProgramSyncRequest {
    private String sourceSystem;
    private String losProgramId;
    private String programCode;
    private String programName;
    private String productType;
    private String lenderId;
    private BigDecimal programLimit;
    private BigDecimal maxBorrowerLimit;
    private BigDecimal defaultInterestRate;
    private Integer maxTenureDays;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String lmsEntryIn;
    private String encoreProductCode;
    /** When false (default), PLP creates program as DRAFT pending LOS L2 approval. */
    private Boolean preApproved;

    private java.math.BigDecimal dependencyVintagePercent;
    private Integer anchorRelationshipVintageMonths;
    private String interestPayment;
    private Integer maxInvoiceVintageDays;
    private Integer maxCmr;
    private Integer minCibil;
    /** Dynamic custom + system field bag for PLP program config merge. */
    private Map<String, Object> customFields;
}
