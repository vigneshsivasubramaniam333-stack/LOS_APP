
import java.time.LocalDate;
import com.sensei.encore.basedomain.model.calendar.Calendar;
import com.sensei.encore.basedomain.model.calendar.Calendar.DateRecurrenceMode;
import com.sensei.encore.basedomain.model.calendar.Tenure;
import com.sensei.encore.basedomain.model.calendar.TimeUnit;

String anchor = (udfTextList.size()>0)? udfTextList.get(0) : null;

if (anchor != null && anchor.equals("JKL")) {
	if (disbursementDate.dayOfMonth >= 1 && disbursementDate.dayOfMonth <= 15)
		repaymentDate = calendar.moveByTenureOnce(disbursementDate, new Tenure(1, TimeUnit.MONTH), disbursementDate, DateRecurrenceMode.DAY_PATTERN, "15");
	else
		repaymentDate = calendar.moveByTenureOnce(disbursementDate, new Tenure(0, TimeUnit.MONTH), disbursementDate, DateRecurrenceMode.DAY_PATTERN, "15");
} else if (anchor != null && anchor.equals("XYZ")) {
	if (disbursementDate.dayOfMonth >= 1 && disbursementDate.dayOfMonth <= 20)
		repaymentDate = calendar.moveByTenureOnce(disbursementDate, new Tenure(1, TimeUnit.MONTH), disbursementDate, DateRecurrenceMode.DAY_PATTERN, "20");
	else
		repaymentDate = calendar.moveByTenureOnce(disbursementDate, new Tenure(0, TimeUnit.MONTH), disbursementDate, DateRecurrenceMode.DAY_PATTERN, "20");
} else {
	if (disbursementDate.dayOfMonth > 5 && disbursementDate.dayOfMonth <= 20)
		repaymentDate = calendar.moveByTenureOnce(disbursementDate, new Tenure(0, TimeUnit.MONTH), disbursementDate, DateRecurrenceMode.DAY_PATTERN, "5");	
	else
		repaymentDate = calendar.moveByTenureOnce(disbursementDate, new Tenure(1, TimeUnit.MONTH), disbursementDate, DateRecurrenceMode.DAY_PATTERN, "5");	
}
repaymentDate1=repaymentDate;
repaymentDate2=repaymentDate;

/*

This script can be customized to provide different repayment dates based on an UDF (udf_text1 assumed to be 'Anchor' here) for loans requiring broken period interest demands.
The script name should read 'ProductCode'+FirstRepaymentDatePicker.groovy. For e.g., if the product code is 201, script name should be 201FirstRepaymentDatePicker.groovy
Loan Product 'Demand date mode' setting should be DAY_NUMBER_OF_REFERENCE_DATE.

The code above is for the first repayment dates defined in table below:

Disbursement date					BPI Demand Date		EMI Date	Anchor Name
From 05-Nov-2020 to 20-Nov-2020		NA					05-Dec-20	ABC
From 21-Nov-2020 to 04-Dec-2020		05-Dec-20			05-Jan-21	ABC
 	 	 	 
From 01-Nov-2020 to 14-Nov-2020		15-Nov-20			15-Dec-20	JKL
From 15-Nov-2020 to 30-Nov-2020		NA					15-Dec-20	JKL
 	 	 	 
From 01-Nov-2020 to 19-Nov-2020		20-Nov-20			20-Dec-20	XYZ
From 20-Nov-2020 to 30-Nov-2020		NA					20-Dec-20	XYZ

*/