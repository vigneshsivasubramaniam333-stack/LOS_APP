import java.time.*
import com.sensei.encore.basedomain.model.calendar.Tenure
import com.sensei.encore.basedomain.model.calendar.TimeUnit

LocalDate referenceDate = LocalDate.of(disbursementDate.getYear(), disbursementDate.getMonth(), 10);
LocalDate repaymentDate1 = calendar.moveByTenure(disbursementDate, new Tenure(0, TimeUnit.MONTH), referenceDate, true);
LocalDate repaymentDate2 = calendar.moveByTenure(disbursementDate, new Tenure(1, TimeUnit.MONTH), referenceDate, true);
if (disbursementDate.compareTo(repaymentDate1) == 0) {
 repaymentDate1 = repaymentDate2;
 repaymentDate2 = calendar.moveByTenure(disbursementDate, new Tenure(2, TimeUnit.MONTH), disbursementDate, true);
}

if (disbursementDate.dayOfMonth > 10 && disbursementDate.dayOfMonth < 20)
	repaymentDate = repaymentDate1;
else
	repaymentDate = repaymentDate2;
