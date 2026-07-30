package com.los.plp.model.enums;

public enum ProgramApprovalStatus {
    DRAFT,
    PENDING_L2,
    SENT_BACK,
    /** L2 approved; waiting for Operations document verification before ACTIVE */
    APPROVED_PENDING_DOCS,
    APPROVED,
    REJECTED
}
