import java.time.LocalDate;
import com.sensei.encore.basedomain.model.money.Money;
import com.sensei.encore.basedomain.model.money.PercentRate;

PercentRate normalInterestRate = loanOdProfile.getNormalInterestRate();
PercentRate colenderInterestRate = new PercentRate(loanOdProfile.getUdfText1());
PercentRate colenderLendingRatio = new PercentRate(loanOdProfile.getUdfText2());
String param = repaymentScheduleProcessorParam;

for (int i = 0; i < scheduleDemands.size(); i++) {
	Money colenderBalance = scheduleDemands.get(i).getBalance().multiply(colenderLendingRatio);
	Money colenderInterest = normalInterestRate.isZero()? new Money (0, "INR"): scheduleDemands.get(i).getNormalInterest().multiply(colenderInterestRate.getMagnitude()).divide(normalInterestRate.getMagnitude());
	Money colenderPrincipal = scheduleDemands.get(i).getPrincipal().multiply(colenderLendingRatio);
	Money colenderAmount = colenderPrincipal.add(colenderInterest);
	Money colenderDue = scheduleDemands.get(i).getAmountDue().min(colenderAmount);
	if (param != null && param.equals("1")) {
		scheduleDemands.get(i).setBalance(colenderBalance);	
		scheduleDemands.get(i).setNormalInterest(colenderInterest);	
		scheduleDemands.get(i).setPrincipal(colenderPrincipal);
		scheduleDemands.get(i).setAmount(colenderAmount);
		scheduleDemands.get(i).setAmountDue(colenderDue);
	} else {
		scheduleDemands.get(i).setBalance(scheduleDemands.get(i).getBalance().subtract(colenderBalance));	
		scheduleDemands.get(i).setNormalInterest(scheduleDemands.get(i).getNormalInterest().subtract(colenderInterest));	
		scheduleDemands.get(i).setPrincipal(scheduleDemands.get(i).getPrincipal().subtract(colenderPrincipal));
		scheduleDemands.get(i).setAmount(scheduleDemands.get(i).getPrincipal().add(scheduleDemands.get(i).getNormalInterest()));
		scheduleDemands.get(i).setAmountDue(scheduleDemands.get(i).getAmountDue().subtract(colenderDue));
	}
}

