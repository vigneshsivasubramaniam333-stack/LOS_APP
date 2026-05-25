import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import com.sensei.encore.loandomain.model.ComputedDemand;

DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
demands = loanOdAccountService.computeRepaymentSchedule(accountId, 1);
for (ComputedDemand demand : demands) {
	if (demand.getDemandDate().equals(LocalDate.parse(demandDate, formatter))) {
		amount = demand.getAmount().getMagnitude();
		break;
	}
}
