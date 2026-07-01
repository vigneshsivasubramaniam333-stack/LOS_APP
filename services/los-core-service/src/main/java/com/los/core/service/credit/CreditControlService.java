package com.los.core.service.credit;

import com.los.core.model.dto.request.ManualCreditInputsRequest;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.kyc.IKycOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.los.core.service.credit.CreditControlKeys.*;

@Service
@RequiredArgsConstructor
public class CreditControlService {

    private static final BigDecimal DEMO_DEFAULT_MONTHLY_INCOME = new BigDecimal("80000");
    private static final BigDecimal DEMO_DEFAULT_MONTHLY_OBLIGATION = new BigDecimal("20000");
    private static final int DEMO_DEFAULT_BUREAU_SCORE = 720;
    private static final BigDecimal DEMO_DEFAULT_FOIR_RATIO = new BigDecimal("0.25");
  /** Conservative placeholders when bank-statement extraction is not wired yet (does not override real inputs). */
    private static final BigDecimal GAP_DEFAULT_MONTHLY_INCOME = new BigDecimal("85000");
    private static final BigDecimal GAP_DEFAULT_MONTHLY_OBLIGATION = new BigDecimal("15000");
    private static final BigDecimal GAP_DEFAULT_AVERAGE_BANK_BALANCE = new BigDecimal("120000");
    private static final BigDecimal GAP_DEFAULT_FOIR_PERCENT = new BigDecimal("25");

    private final IKycOrchestrationService kycOrchestrationService;

    @SuppressWarnings("unchecked")
    public void mergeManualInputs(LoanApplication app, ManualCreditInputsRequest req) {
        Map<String, Object> fi = app.getFinancialInfo() != null
                ? new HashMap<>(app.getFinancialInfo())
                : new HashMap<>();
        Map<String, Object> cc = fi.get(ROOT) instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
        Map<String, Object> manual = cc.get(MANUAL) instanceof Map<?, ?> m2
                ? new LinkedHashMap<>((Map<String, Object>) m2)
                : new LinkedHashMap<>();

        if (req.getPanName() != null) {
            putManualField(manual, "panName", req.getPanName());
        }
        if (req.getPanStatus() != null) {
            putManualField(manual, "panStatus", req.getPanStatus());
        }
        if (req.getAadhaarName() != null) {
            putManualField(manual, "aadhaarName", req.getAadhaarName());
        }
        if (req.getAadhaarStatus() != null) {
            putManualField(manual, "aadhaarStatus", req.getAadhaarStatus());
        }
        if (req.getMobileVerified() != null) {
            putManualField(manual, "mobileVerified", req.getMobileVerified());
        }
        if (req.getMonthlyIncome() != null) {
            putManualField(manual, "monthlyIncome", req.getMonthlyIncome().toPlainString());
        }
        if (req.getMonthlyObligation() != null) {
            putManualField(manual, "monthlyObligation", req.getMonthlyObligation().toPlainString());
        }
        if (req.getGstIncome() != null) {
            putManualField(manual, "gstIncome", req.getGstIncome().toPlainString());
        }
        if (req.getBankStatementIncome() != null) {
            putManualField(manual, "bankStatementIncome", req.getBankStatementIncome().toPlainString());
        }
        if (req.getAverageBankBalance() != null) {
            putManualField(manual, "averageBankBalance", req.getAverageBankBalance().toPlainString());
        }
        if (req.getObligationRatio() != null) {
            putManualField(manual, "obligationRatio", req.getObligationRatio().toPlainString());
        }
        if (req.getEmiObligation() != null) {
            putManualField(manual, "emiObligation", req.getEmiObligation().toPlainString());
        }
        if (req.getPropertyValue() != null) {
            putManualField(manual, "propertyValue", req.getPropertyValue().toPlainString());
        }
        if (req.getLtv() != null) {
            putManualField(manual, "ltv", req.getLtv().toPlainString());
        }
        if (req.getBusinessVintageMonths() != null) {
            putManualField(manual, "businessVintageMonths", String.valueOf(req.getBusinessVintageMonths()));
        }
        if (req.getIndustryRisk() != null) {
            putManualField(manual, "industryRisk", req.getIndustryRisk().trim().toUpperCase());
        }
        if (req.getRepaymentHistory() != null) {
            putManualField(manual, "repaymentHistory", req.getRepaymentHistory().trim().toUpperCase());
        }
        if (req.getEbitdaProxy() != null) {
            putManualField(manual, "ebitdaProxy", req.getEbitdaProxy().toPlainString());
        }
        if (req.getLeverageRatio() != null) {
            putManualField(manual, "leverageRatio", req.getLeverageRatio().toPlainString());
        }
        if (req.getSupportingDocumentIds() != null && !req.getSupportingDocumentIds().isEmpty()) {
            List<String> ids = req.getSupportingDocumentIds().stream().map(UUID::toString).toList();
            putManualField(manual, "supportingDocumentIds", ids);
        }
        if (req.getState() != null) {
            putManualField(manual, "state", req.getState());
        }
        if (req.getCity() != null) {
            putManualField(manual, "city", req.getCity());
        }
        if (req.getCreditRemarks() != null) {
            putManualField(manual, "creditRemarks", req.getCreditRemarks());
        }
        if (req.getManualKycOutcome() != null) {
            putManualField(manual, "manualKycOutcome", req.getManualKycOutcome().trim().toUpperCase());
        }

        if (req.getManualBureauScore() != null) {
            app.setManualBureauScore(req.getManualBureauScore());
            putManualField(manual, "bureauScore", String.valueOf(req.getManualBureauScore()));
        }
        if (req.getManualBureauRemarks() != null) {
            app.setManualBureauRemarks(req.getManualBureauRemarks());
            putManualField(manual, "bureauRemarks", req.getManualBureauRemarks());
        }

        mergeScorecardMetricField(manual, "avgDailyBalance3m", req.getAvgDailyBalance3m());
        mergeScorecardMetricField(manual, "avgMonthlyTransactions3m", req.getAvgMonthlyTransactions3m());
        mergeScorecardMetricField(manual, "avgMonthlySettlements3m", req.getAvgMonthlySettlements3m());
        mergeScorecardMetricField(manual, "monthlyTransactions3m", req.getMonthlyTransactions3m());
        mergeScorecardMetricField(manual, "inwardChequeReturns3m", req.getInwardChequeReturns3m());
        mergeScorecardMetricField(manual, "avgDailySettlements3m", req.getAvgDailySettlements3m());
        mergeScorecardMetricField(manual, "noOfTxns60days", req.getNoOfTxns60days());
        mergeScorecardMetricField(manual, "txnMth1", req.getTxnMth1());
        mergeScorecardMetricField(manual, "txnMth2", req.getTxnMth2());
        mergeScorecardMetricField(manual, "txnMth3", req.getTxnMth3());
        mergeScorecardMetricField(manual, "avgGmv3m", req.getAvgGmv3m());
        mergeScorecardMetricField(manual, "active90days", req.getActive90days());
        mergeScorecardYesNoField(manual, "residenceOwned", req.getResidenceOwned());
        mergeScorecardMetricField(manual, "residenceStability", req.getResidenceStability());
        mergeScorecardMetricField(manual, "businessStability", req.getBusinessStability());
        mergeScorecardYesNoField(manual, "existingLoanTrackRecordAll", req.getExistingLoanTrackRecordAll());
        mergeScorecardYesNoField(manual, "existingLoanTrackRecord15d", req.getExistingLoanTrackRecord15d());
        mergeScorecardYesNoField(manual, "qrTxnEDI", req.getQrTxnEDI());
        mergeScorecardYesNoField(manual, "eligibleOnePointFiveX", req.getEligibleOnePointFiveX());
        if (req.getScorecardMetrics() != null && !req.getScorecardMetrics().isEmpty()) {
            Map<String, Object> existing = manual.get("scorecardMetrics") instanceof Map<?, ?> m
                    ? new LinkedHashMap<>((Map<String, Object>) m)
                    : new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : req.getScorecardMetrics().entrySet()) {
                if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null) {
                    putManualField(existing, e.getKey().trim(), e.getValue());
                }
            }
            manual.put("scorecardMetrics", existing);
        }

        if (req.getDecisionSources() != null) {
            Map<String, Object> ds = new LinkedHashMap<>();
            if (req.getDecisionSources().getBureauScoreSource() != null) {
                ds.put("bureauScoreSource", normalizeSource(req.getDecisionSources().getBureauScoreSource()));
            }
            if (req.getDecisionSources().getIncomeSource() != null) {
                ds.put("incomeSource", normalizeSource(req.getDecisionSources().getIncomeSource()));
            }
            if (req.getDecisionSources().getKycSource() != null) {
                ds.put("kycSource", normalizeSource(req.getDecisionSources().getKycSource()));
            }
            cc.put(DECISION_SOURCES, mergeDecisionSources(
                    (Map<String, Object>) Optional.ofNullable(cc.get(DECISION_SOURCES)).orElse(Map.of()),
                    ds));
        }

        cc.put(MANUAL, manual);
        cc.put(PROVIDER_SNAPSHOT, buildProviderSnapshot(app));
        fi.put(ROOT, cc);
        app.setFinancialInfo(fi);
    }

    /**
     * Records a controlled KYC process override so underwriting can proceed using manual KYC pass
     * even when provider/computed KYC outcome is FAIL.
     */
    @SuppressWarnings("unchecked")
    public void applyManualKycPassOnProcessOverride(LoanApplication app, String remarks) {
        Map<String, Object> fi = app.getFinancialInfo() != null
                ? new LinkedHashMap<>(app.getFinancialInfo())
                : new LinkedHashMap<>();
        Map<String, Object> cc = fi.get(ROOT) instanceof Map<?, ?> existingCc
                ? new LinkedHashMap<>((Map<String, Object>) existingCc)
                : new LinkedHashMap<>();
        Map<String, Object> manual = cc.get(MANUAL) instanceof Map<?, ?> existingManual
                ? new LinkedHashMap<>((Map<String, Object>) existingManual)
                : new LinkedHashMap<>();
        Map<String, Object> ds = cc.get(DECISION_SOURCES) instanceof Map<?, ?> existingDs
                ? new LinkedHashMap<>((Map<String, Object>) existingDs)
                : new LinkedHashMap<>();

        putManualField(manual, "manualKycOutcome", "PASS");
        if (remarks != null && !remarks.isBlank()) {
            putManualField(manual, "kycOverrideRemarks", remarks.trim());
        }
        ds.put("kycSource", SRC_MANUAL);
        cc.put(MANUAL, manual);
        cc.put(DECISION_SOURCES, ds);
        fi.put(ROOT, cc);
        app.setFinancialInfo(fi);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeDecisionSources(Map<String, Object> old, Map<String, Object> updates) {
        Map<String, Object> o = new LinkedHashMap<>(old);
        o.putAll(updates);
        return o;
    }

    private void putManualField(Map<String, Object> manual, String key, Object value) {
        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("value", value);
        cell.put("source", SRC_MANUAL);
        cell.put("updatedAt", Instant.now().toString());
        manual.put(key, cell);
    }

    private void mergeScorecardMetricField(Map<String, Object> manual, String key, Object value) {
        if (value == null) {
            return;
        }
        putManualField(manual, key, value instanceof BigDecimal b ? b.toPlainString() : value);
    }

    private void mergeScorecardYesNoField(Map<String, Object> manual, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        putManualField(manual, key, value.trim().toUpperCase());
    }

    private static void putScorecardValue(Map<String, Object> manual, Map<String, BigDecimal> out, String key, String manKey) {
        if (manual.get(manKey) == null) {
            return;
        }
        BigDecimal b = toYesNoOrNumberBd(unwrapValue(manual.get(manKey)));
        if (b != null) {
            out.put(key, b);
        }
    }

    private static void putScorecardValueExact(Map<String, Object> manual, Map<String, BigDecimal> out, String key) {
        putScorecardValue(manual, out, key, key);
    }

    @SuppressWarnings("unchecked")
    private static void mergeScorecardMetricsMap(Map<String, Object> manual, Map<String, BigDecimal> out) {
        Object raw = manual.get("scorecardMetrics");
        if (!(raw instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = String.valueOf(e.getKey()).trim();
            if (key.isEmpty()) {
                continue;
            }
            BigDecimal b = toYesNoOrNumberBd(unwrapValue(e.getValue()));
            if (b != null) {
                out.put(key, b);
            }
        }
    }

    public Map<String, Object> buildProviderSnapshot(LoanApplication app) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("bureauScore", app.getBureauScore());
        String k = "";
        try {
            k = String.valueOf(kycOrchestrationService.computeKycOutcome(app.getId())
                    .getOrDefault("outcome", ""));
        } catch (Exception e) {
            k = "UNKNOWN";
        }
        snap.put("kycOutcome", k);
        if (app.getPersonalInfo() != null) {
            if (app.getPersonalInfo().get("panName") != null) {
                snap.put("panName", str(app.getPersonalInfo().get("panName")));
            }
            if (app.getPersonalInfo().get("state") != null) {
                snap.put("state", str(app.getPersonalInfo().get("state")));
            }
            if (app.getPersonalInfo().get("city") != null) {
                snap.put("city", str(app.getPersonalInfo().get("city")));
            }
        }
        if (app.getFinancialInfo() != null && app.getFinancialInfo().get("monthlyIncome") != null) {
            snap.put("monthlyIncome", str(app.getFinancialInfo().get("monthlyIncome")));
        }
        snap.put("capturedAt", Instant.now().toString());
        return snap;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    public EffectiveUnderwritingContext resolveEffective(LoanApplication app, String computedKycOutcome) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fi = app.getFinancialInfo() != null ? app.getFinancialInfo() : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> cc = (Map<String, Object>) fi.getOrDefault(ROOT, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> ds = (Map<String, Object>) cc.getOrDefault(DECISION_SOURCES, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> manual = (Map<String, Object>) cc.getOrDefault(MANUAL, Map.of());

        String bureauSource = str(ds.get("bureauScoreSource"));
        if (bureauSource == null || bureauSource.isBlank()) {
            bureauSource = (app.getManualBureauScore() != null && app.getManualBureauScore() > 0)
                    ? SRC_MANUAL
                    : SRC_PROVIDER;
        }
        String incomeSource = Optional.ofNullable(str(ds.get("incomeSource"))).filter(s -> !s.isBlank()).orElse(SRC_PROVIDER);
        String kycSource = Optional.ofNullable(str(ds.get("kycSource"))).filter(s -> !s.isBlank()).orElse(SRC_PROVIDER);

        int effBureau = resolveBureau(app, manual, bureauSource);
        boolean kycPass = resolveKyc(kycSource, manual, computedKycOutcome);
        BigDecimal inc = resolveIncome(app, fi, manual, incomeSource);
        BigDecimal obl = resolveObligation(fi, manual, incomeSource);
        boolean safeDemoFallback = shouldApplySafeFallback(app, fi, cc, inc, obl);
        if (safeDemoFallback) {
            if (effBureau <= 0) {
                effBureau = DEMO_DEFAULT_BUREAU_SCORE;
                bureauSource = "DEMO_FALLBACK";
            }
            if (inc == null || inc.compareTo(BigDecimal.ZERO) <= 0) {
                inc = DEMO_DEFAULT_MONTHLY_INCOME;
                incomeSource = "DEMO_FALLBACK";
            }
            if (obl == null || obl.compareTo(BigDecimal.ZERO) < 0) {
                obl = DEMO_DEFAULT_MONTHLY_OBLIGATION;
            }
            if (!kycPass && !isHardKycFail(computedKycOutcome)) {
                kycPass = true;
                kycSource = "DEMO_FALLBACK";
            }
        }
        String st = null;
        if (manual.get("state") != null) {
            st = str(unwrapValue(manual.get("state")));
        } else if (app.getPersonalInfo() != null && app.getPersonalInfo().get("state") != null) {
            st = str(app.getPersonalInfo().get("state"));
        }
        String city = null;
        if (manual.get("city") != null) {
            city = str(unwrapValue(manual.get("city")));
        } else if (app.getPersonalInfo() != null && app.getPersonalInfo().get("city") != null) {
            city = str(app.getPersonalInfo().get("city"));
        }

        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        putScorecardValue(manual, sc, "GST_INCOME", "gstIncome");
        putScorecardValue(manual, sc, "BANK_STATEMENT_INCOME", "bankStatementIncome");
        putScorecardValue(manual, sc, "AVERAGE_BANK_BALANCE", "averageBankBalance");
        putScorecardValue(manual, sc, "PROPERTY_VALUE", "propertyValue");
        putScorecardValue(manual, sc, "EBITDA_PROXY", "ebitdaProxy");
        putScorecardValue(manual, sc, "LEVERAGE_RATIO", "leverageRatio");
        putScorecardValue(manual, sc, "BUSINESS_VINTAGE_MONTHS", "businessVintageMonths");
        putScorecardValueExact(manual, sc, "avgDailyBalance3m");
        putScorecardValueExact(manual, sc, "avgMonthlyTransactions3m");
        putScorecardValueExact(manual, sc, "avgMonthlySettlements3m");
        putScorecardValueExact(manual, sc, "monthlyTransactions3m");
        putScorecardValueExact(manual, sc, "inwardChequeReturns3m");
        putScorecardValueExact(manual, sc, "avgDailySettlements3m");
        putScorecardValueExact(manual, sc, "noOfTxns60days");
        putScorecardValueExact(manual, sc, "txnMth1");
        putScorecardValueExact(manual, sc, "txnMth2");
        putScorecardValueExact(manual, sc, "txnMth3");
        putScorecardValueExact(manual, sc, "avgGmv3m");
        putScorecardValueExact(manual, sc, "active90days");
        putScorecardValueExact(manual, sc, "residenceOwned");
        putScorecardValueExact(manual, sc, "residenceStability");
        putScorecardValueExact(manual, sc, "businessStability");
        putScorecardValueExact(manual, sc, "existingLoanTrackRecordAll");
        putScorecardValueExact(manual, sc, "existingLoanTrackRecord15d");
        putScorecardValueExact(manual, sc, "qrTxnEDI");
        putScorecardValueExact(manual, sc, "eligibleOnePointFiveX");
        mergeScorecardMetricsMap(manual, sc);
        if (inc != null) {
            sc.put("MONTHLY_INCOME", inc);
        }
        if (obl != null) {
            sc.put("EMI_OBLIGATION", obl);
        }
        if (manual.get("emiObligation") != null) {
            BigDecimal emiO = toBd(unwrapValue(manual.get("emiObligation")));
            if (emiO != null) {
                sc.put("EMI_OBLIGATION", emiO);
            }
        }
        BigDecimal ratio = null;
        if (manual.get("obligationRatio") != null) {
            ratio = toBd(unwrapValue(manual.get("obligationRatio")));
        }
        if (ratio == null && inc != null && obl != null && inc.compareTo(BigDecimal.ZERO) > 0) {
            ratio = obl.divide(inc, 6, RoundingMode.HALF_UP);
        }
        if (ratio == null && safeDemoFallback) {
            ratio = DEMO_DEFAULT_FOIR_RATIO;
        }
        if (ratio != null) {
            sc.put("OBLIGATION_RATIO", ratio.multiply(BigDecimal.valueOf(100)));
        }
        applyMissingScorecardDefaults(app, sc, inc, obl);
        if (inc == null && sc.get("MONTHLY_INCOME") != null) {
            inc = sc.get("MONTHLY_INCOME");
        }
        if (obl == null && sc.get("EMI_OBLIGATION") != null) {
            obl = sc.get("EMI_OBLIGATION");
        }
        if (manual.get("ltv") != null) {
            putScorecardValue(manual, sc, "LTV", "ltv");
        } else if (app.getRequestedAmount() != null) {
            BigDecimal prop = sc.get("PROPERTY_VALUE");
            if (prop != null && prop.compareTo(BigDecimal.ZERO) > 0) {
                sc.put("LTV", app.getRequestedAmount()
                        .divide(prop, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
            }
        }
        String ind = str(unwrapValue(manual.get("industryRisk")));
        if (ind != null && !ind.isBlank()) {
            sc.put("INDUSTRY_RISK", "LOW".equalsIgnoreCase(ind) ? BigDecimal.ONE : BigDecimal.ZERO);
        }
        String rep = str(unwrapValue(manual.get("repaymentHistory")));
        if (rep != null && !rep.isBlank()) {
            sc.put("REPAYMENT_HISTORY", "CLEAN".equalsIgnoreCase(rep) ? BigDecimal.ONE : BigDecimal.ZERO);
        }
        if (kycPass) {
            sc.put("KYC_QUALITY", BigDecimal.ONE);
        } else {
            sc.put("KYC_QUALITY", BigDecimal.ZERO);
        }
        if (safeDemoFallback) {
            sc.put("DEMO_FALLBACK_ACTIVE", BigDecimal.ONE);
        }
        sc.put("BUREAU_SCORE", BigDecimal.valueOf(effBureau));
        return new EffectiveUnderwritingContext(
                effBureau, kycPass, inc, obl, st, city, bureauSource, incomeSource, kycSource, sc);
    }

    private static boolean shouldApplySafeFallback(
            LoanApplication app,
            Map<String, Object> fi,
            Map<String, Object> cc,
            BigDecimal income,
            BigDecimal obligation) {
        return isExplicitDemo(fi) || isExplicitDemo(cc) || isExplicitDemo(app.getPersonalInfo());
    }

    private static boolean isExplicitDemo(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        Object[] flags = new Object[] {
                data.get("demo"),
                data.get("isDemo"),
                data.get("demoMode"),
                data.get("testMode"),
                data.get("sandbox")
        };
        for (Object flag : flags) {
            if (asBoolean(flag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        String s = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(s)
                || "yes".equalsIgnoreCase(s)
                || "1".equals(s)
                || "demo".equalsIgnoreCase(s);
    }

    private static boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return String.valueOf(value).trim().isEmpty();
    }

    private static boolean isHardKycFail(String outcome) {
        if (outcome == null) {
            return false;
        }
        String k = outcome.trim().toUpperCase();
        return "FAIL".equals(k) || "FAILED".equals(k) || "REJECT".equals(k) || "REJECTED".equals(k);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buildReadView(LoanApplication app) {
        Map<String, Object> view = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> fi = app.getFinancialInfo() != null ? app.getFinancialInfo() : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> cc = (Map<String, Object>) fi.getOrDefault(ROOT, Map.of());
        view.put("creditControl", cc);
        view.put("providerBureauScore", app.getBureauScore());
        view.put("manualBureauScore", app.getManualBureauScore());
        String computedKyc;
        try {
            computedKyc = String.valueOf(kycOrchestrationService.computeKycOutcome(app.getId()).getOrDefault("outcome", ""));
        } catch (Exception e) {
            computedKyc = "UNKNOWN";
        }
        view.put("computedKycOutcome", computedKyc);
        var ctx = resolveEffective(app, computedKyc);
        view.put("effective", ctx.toMap());
        return view;
    }

    private static int resolveBureau(LoanApplication app, Map<String, Object> manual, String bureauSource) {
        if (SRC_MANUAL.equalsIgnoreCase(bureauSource)) {
            if (app.getManualBureauScore() != null && app.getManualBureauScore() > 0) {
                return app.getManualBureauScore();
            }
            if (manual.get("bureauScore") != null) {
                try {
                    return Integer.parseInt(str(unwrapValue(manual.get("bureauScore"))));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            return 0;
        }
        if (app.getBureauScore() != null && app.getBureauScore() > 0) {
            return app.getBureauScore();
        }
        if (app.getManualBureauScore() != null && app.getManualBureauScore() > 0) {
            return app.getManualBureauScore();
        }
        return 0;
    }

    private static boolean resolveKyc(String kycSource, Map<String, Object> manual, String computed) {
        if (SRC_MANUAL.equalsIgnoreCase(kycSource)) {
            Object o = manual.get("manualKycOutcome");
            if (o == null) {
                return false;
            }
            String s = str(unwrapValue(o));
            return "PASS".equalsIgnoreCase(s);
        }
        return "PASS".equalsIgnoreCase(String.valueOf(computed).trim());
    }

    private static BigDecimal resolveIncome(
            LoanApplication app, Map<String, Object> fi, Map<String, Object> manual, String incomeSource) {
        if (SRC_MANUAL.equalsIgnoreCase(incomeSource) && manual.get("monthlyIncome") != null) {
            return toBd(unwrapValue(manual.get("monthlyIncome")));
        }
        BigDecimal fromFi = toBd(fi.get("monthlyIncome"));
        if (fromFi != null && fromFi.compareTo(BigDecimal.ZERO) > 0) {
            return fromFi;
        }
        if (app.getPersonalInfo() != null) {
            BigDecimal fromProfile = toBd(app.getPersonalInfo().get("monthlyNetIncome"));
            if (fromProfile != null && fromProfile.compareTo(BigDecimal.ZERO) > 0) {
                return fromProfile;
            }
        }
        if (app.getBusinessInfo() != null) {
            BigDecimal fromBusiness = toBd(app.getBusinessInfo().get("monthlyIncome"));
            if (fromBusiness != null && fromBusiness.compareTo(BigDecimal.ZERO) > 0) {
                return fromBusiness;
            }
        }
        return null;
    }

    /**
     * Fills scorecard parameters that normally depend on bank-statement extraction when no verified value exists.
     * Never overwrites keys already present in the effective scorecard map.
     */
    private static void applyMissingScorecardDefaults(
            LoanApplication app, Map<String, BigDecimal> sc, BigDecimal income, BigDecimal obligation) {
        boolean applied = false;
        if (!sc.containsKey("MONTHLY_INCOME") || isZeroOrMissing(sc.get("MONTHLY_INCOME"))) {
            BigDecimal fallback = income;
            if (fallback == null || fallback.compareTo(BigDecimal.ZERO) <= 0) {
                fallback = incomeFromProfile(app);
            }
            if (fallback == null || fallback.compareTo(BigDecimal.ZERO) <= 0) {
                fallback = GAP_DEFAULT_MONTHLY_INCOME;
            }
            sc.put("MONTHLY_INCOME", fallback);
            applied = true;
        }
        if (!sc.containsKey("EMI_OBLIGATION") || isZeroOrMissing(sc.get("EMI_OBLIGATION"))) {
            BigDecimal fallback = obligation;
            if (fallback == null || fallback.compareTo(BigDecimal.ZERO) < 0) {
                fallback = GAP_DEFAULT_MONTHLY_OBLIGATION;
            }
            sc.put("EMI_OBLIGATION", fallback);
            applied = true;
        }
        if (!sc.containsKey("OBLIGATION_RATIO") || isZeroOrMissing(sc.get("OBLIGATION_RATIO"))) {
            BigDecimal inc = sc.get("MONTHLY_INCOME");
            BigDecimal obl = sc.get("EMI_OBLIGATION");
            if (inc != null && inc.compareTo(BigDecimal.ZERO) > 0 && obl != null) {
                sc.put(
                        "OBLIGATION_RATIO",
                        obl.divide(inc, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
            } else {
                sc.put("OBLIGATION_RATIO", GAP_DEFAULT_FOIR_PERCENT);
            }
            applied = true;
        }
        if (!sc.containsKey("AVERAGE_BANK_BALANCE") || isZeroOrMissing(sc.get("AVERAGE_BANK_BALANCE"))) {
            sc.put("AVERAGE_BANK_BALANCE", GAP_DEFAULT_AVERAGE_BANK_BALANCE);
            applied = true;
        }
        if (applied) {
            sc.put("PROVIDER_GAP_DEFAULT_ACTIVE", BigDecimal.ONE);
        }
    }

    private static BigDecimal incomeFromProfile(LoanApplication app) {
        if (app.getPersonalInfo() != null) {
            BigDecimal fromProfile = toBd(app.getPersonalInfo().get("monthlyNetIncome"));
            if (fromProfile != null && fromProfile.compareTo(BigDecimal.ZERO) > 0) {
                return fromProfile;
            }
        }
        if (app.getBusinessInfo() != null) {
            BigDecimal fromBusiness = toBd(app.getBusinessInfo().get("monthlyIncome"));
            if (fromBusiness != null && fromBusiness.compareTo(BigDecimal.ZERO) > 0) {
                return fromBusiness;
            }
        }
        return null;
    }

    private static boolean isZeroOrMissing(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0;
    }

    private static BigDecimal resolveObligation(Map<String, Object> fi, Map<String, Object> manual, String incomeSource) {
        if (SRC_MANUAL.equalsIgnoreCase(incomeSource) && manual.get("monthlyObligation") != null) {
            return toBd(unwrapValue(manual.get("monthlyObligation")));
        }
        if (fi.get("monthlyObligation") != null) {
            return toBd(fi.get("monthlyObligation"));
        }
        if (fi.get("obligation") != null) {
            return toBd(fi.get("obligation"));
        }
        return null;
    }

    private static Object unwrapValue(Object cell) {
        if (cell instanceof Map<?, ?> m && m.get("value") != null) {
            return m.get("value");
        }
        return cell;
    }

    private static String normalizeSource(String s) {
        if (s == null) {
            return SRC_PROVIDER;
        }
        String t = s.trim().toUpperCase();
        if ("PROVIDER".equals(t) || "MANUAL".equals(t) || "SYSTEM".equals(t)) {
            return t;
        }
        return SRC_PROVIDER;
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal toYesNoOrNumberBd(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        if ("Y".equalsIgnoreCase(s) || "YES".equalsIgnoreCase(s)) {
            return BigDecimal.ONE;
        }
        if ("N".equalsIgnoreCase(s) || "NO".equalsIgnoreCase(s)) {
            return BigDecimal.ZERO;
        }
        return toBd(o);
    }
}
