import java.time.LocalDate;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import com.sensei.encore.basedomain.model.calendar.Calendar;

// Script for maturityDate computation
// should be productCode + MaturityDatePicker.groovy
// this returns minimum(maturity date) of loans with same customer limit

if (customerLimit == null)
	return maturityDate;
String maturityDateQuery = "select min(lw.maturity_date) from loan_od_working_registers lw, account_holders ah, loan_od_profiles lp where " +
		"lw.account_id = ah.account_id and lw.tenant_code = ah.tenant_code and ah.holder_num = 1 and lw.account_id = lp.account_id and lw.tenant_code = lp.tenant_code " +
		"and lp.customer_limit_code = :limitCode and ah.customer_id = :customerId and ah.tenant_code = :tenantCode";
Map<String, Object> params = new HashMap<>();
params.put("limitCode", customerLimit.getCode());
params.put("customerId", customerLimit.getCustomerId());
String maturityDateStr = taskRepository.runFindQueryMultipleInputSingleResult(maturityDateQuery, params, 0);
if (StringUtils.isEmpty(maturityDateStr))
	return maturityDate;
return Calendar.parseLocalDate(maturityDateStr);
