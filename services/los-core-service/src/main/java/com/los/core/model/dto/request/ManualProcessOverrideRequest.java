package com.los.core.model.dto.request;

import lombok.Data;

@Data
public class ManualProcessOverrideRequest {
    private String processCode;
    private String failureCode;
    private String overrideReason;
    private String remarks;
    private String approvalReference;
}
