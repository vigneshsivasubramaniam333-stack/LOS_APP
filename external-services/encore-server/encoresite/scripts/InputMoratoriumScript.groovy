
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import com.sensei.encore.basedomain.model.calendar.Tenure;
import com.sensei.encore.basedomain.model.calendar.TimeUnit;
import com.sensei.encore.basedomain.model.money.Money;
import com.sensei.encore.basedomain.model.money.PercentRate;
import com.sensei.encore.basedomain.model.bank.BankRepository;
import com.sensei.encore.loandomain.model.LoanOdMoratorium;
import com.sensei.encore.loandomain.model.LoanOdProduct;

BankRepository bankRepository = applicationContext.getBean(BankRepository.class);
LocalDate currentDate = bankRepository.findBank().getCurrentWorkingDate();
LocalDate firstRepaymentDate = calendar.getNextIntervalDateOnCutOff(account.getOpenedOnDate(), loanOdProduct.getDemandDayPattern(), loanOdProduct.getDemandCutOffDay(), loanOdProfile.getDemandDateMode(), loanOdProfile.getDemandInterval());

if (loanOdProduct.getProductCode().equals("JFSF01")) {
        int days = calendar.daysBetween(account.getOpenedOnDate(), LocalDate.of(account.getOpenedOnDate().getYear(), account.getOpenedOnDate().getMonth(), 16));
        logger.info("firstRepaymentDate, tenure {} {} Days",firstRepaymentDate, days );
        if (days <= 0)
                return;
        Tenure tenure = new Tenure(days, TimeUnit.DAY);
        LoanOdMoratorium t = new LoanOdMoratorium();
        t.setValueDate(currentDate);
        t.setMoratoriumInstallment(new Money(0, loanOdProduct.getCurrencyCode()));
        t.setTenure(tenure);
        t.setStartDate(account.getOpenedOnDate());
        t.setMoratoriumEffect(LoanOdMoratorium.MoratoriumEffect.NONE);
        t.setMoratoriumNormalInterestRate(PercentRate.ZERO);
        t.setReference("createdWithLoan:false");
        t.setMoratoriumType(LoanOdProduct.MoratoriumType.INTEREST_ADJUSTMENT);
        inputMoratoriums = List.of(t);
} else {
        if (loanOdProfile.getSecurityDepositInstallments() == 0 || account.getUserSecurityDeposit() == null || !account.getUserSecurityDeposit().isPositive())
                return;
        Tenure tenure = new Tenure(loanOdProfile.getSecurityDepositInstallments(), loanOdProfile.getDemandInterval().getUnit());
        LocalDate maturityDate = calendar.moveByTenure(firstRepaymentDate, loanOdProfile.getTenure().subtractWithUnitOf(loanOdProfile.getDemandInterval()),
        loanOdProfile.getDemandInterval(), firstRepaymentDate, loanOdProfile.getDemandDateMode(), loanOdProduct.getDemandDayPattern());
        LocalDate startDate = calendar.moveByTenure(maturityDate, tenure.negate());
        Money installment = account.getUserSecurityDeposit().divide(new BigDecimal(loanOdProfile.getSecurityDepositInstallments()), 0, RoundingMode.HALF_UP);
        logger.info("installment,tenure,maturityDate,startDate {} {} {} {}",installment,tenure,maturityDate,startDate );
        LoanOdMoratorium t = new LoanOdMoratorium();
        t.setValueDate(currentDate);
        t.setMoratoriumInstallment(installment);
        t.setTenure(tenure);
        t.setStartDate(startDate);
        t.setMoratoriumEffect(LoanOdMoratorium.MoratoriumEffect.NONE);
        t.setMoratoriumType(LoanOdProduct.MoratoriumType.EQUATED);
        inputMoratoriums = List.of(t);
}

