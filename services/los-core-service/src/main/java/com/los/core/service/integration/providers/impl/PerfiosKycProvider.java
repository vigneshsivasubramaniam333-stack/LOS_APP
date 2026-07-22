package com.los.core.service.integration.providers.impl;

import com.los.core.model.enums.KycStepType;
import com.los.core.service.integration.providers.IKycProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Perfios is the default primary in {@code aggregator_routing} for many KYC steps; implementation
 * currently delegates to {@link KarzaKycProvider} (same API surface) until a dedicated Perfios client is wired.
 * Audit rows are tagged as PERFIOS via {@code _auditProvider}.
 */
@Component("perfiosKycProvider")
@RequiredArgsConstructor
public class PerfiosKycProvider implements IKycProvider {

    private final KarzaKycProvider karzaKycProvider;

    @Override
    public KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload) {
        Map<String, Object> tagged = payload == null ? new HashMap<>() : new HashMap<>(payload);
        tagged.put("_auditProvider", "PERFIOS");
        return karzaKycProvider.verify(stepType, tagged);
    }

    @Override
    public boolean supports(KycStepType stepType) {
        return karzaKycProvider.supports(stepType);
    }

    @Override
    public String getProviderName() {
        return "PERFIOS";
    }

    @Override
    public int getPriority() {
        return karzaKycProvider.getPriority();
    }
}
