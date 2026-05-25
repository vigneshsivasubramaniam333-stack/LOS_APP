import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.item.file.transform.DefaultFieldSet;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.context.ApplicationContext;

import com.sensei.encore.basedomain.model.account.AccountBalance;
import com.sensei.encore.basedomain.model.account.AccountHolder;
import com.sensei.encore.basedomain.model.account.AccountProfile;
import com.sensei.encore.basedomain.model.account.AccountRepository;
import com.sensei.encore.basedomain.model.calendar.Calendar;
import com.sensei.encore.basedomain.model.money.Money;
import com.sensei.encore.loandomain.model.ComputedDemand;
import com.sensei.encore.loandomain.model.LoanOdLenderWorkingRegister;
import com.sensei.encore.loandomain.model.LoanOdProfile;
import com.sensei.encore.loandomain.model.LoanOdWorkingRegister;
import com.sensei.encore.loandomain.operation.ComputeRepaymentScheduleQuery;

String accountId = entityId;
results = new ArrayList<>();
AccountRepository accountRepository = applicationContext.getBean(AccountRepository.class);

ComputeRepaymentScheduleQuery query = new ComputeRepaymentScheduleQuery (accountId, false, false, true, true, null);
query.execute();
LoanOdProfile loanOdProfile = query.getLoanOdProfile();
if (!loanOdProfile.isColendingApplicable())
        return;
LoanOdWorkingRegister loanOdWorkingRegister = query.getLoanOdWorkingRegister();
List<LoanOdLenderWorkingRegister> lenderWorkingRegisters = query.getLoanOdLenderWorkingRegisters();
AccountBalance accountBalance = accountRepository.findAccountBalance(accountId);
AccountProfile accountProfile = query.getAccountProfile();
List<AccountHolder> accountHolders = query.getAccountHolders();
List<ComputedDemand> demandComputationResults = query.getComputedDemands();
List<ComputedDemand> colenderDemandComputationResults = query.getComputedColenderDemands();

Money zero = new Money (0, accountProfile.getCurrencyCode());
int colenderRow = 0;
for (int i = 0; i < demandComputationResults.size(); i++) {
        ComputedDemand d =  demandComputationResults.get(i);
        LocalDate demandDate = d.getDemandDate();
        Money demandPrincipal = d.getPrincipal();
        Money demandInterest = d.getNormalInterest();
        Money demandAmount = d.getAmount();
        Money demandBalance = d.getBalance();
        Money colenderPrincipal = zero, colenderInterest=zero, colenderAmount = zero, colenderBalance = zero;
        if (colenderRow < colenderDemandComputationResults.size()) {
                ComputedDemand colenderDemand = colenderDemandComputationResults.get(colenderRow);
                if (colenderDemand.getDemandDate().isAfter(d.getDemandDate()))
                        continue;
                colenderPrincipal = colenderDemand.getPrincipal();
                colenderInterest = colenderDemand.getNormalInterest();
                colenderAmount = colenderDemand.getAmount();
                colenderBalance = colenderDemand.getBalance();
                colenderRow++;
        }
        Money lenderPrincipal = demandPrincipal.subtract(colenderPrincipal);
        Money lenderInterest = demandInterest.subtract(colenderInterest);
        Money lenderAmount = demandAmount.subtract(colenderAmount);
        Money lenderBalance = demandBalance.subtract(colenderBalance);
        String[] rows = new String[22];
        int col = 0;
        rows[col++] = accountId;
        rows[col++] = accountHolders.get(0).getCustomerName().toString();
        rows[col++] = accountProfile.getBranchCode();
        rows[col++] = lenderWorkingRegisters.get(0).getNormalInterestRate().toValueString();
        rows[col++] = lenderWorkingRegisters.get(1).getNormalInterestRate().toValueString();
        rows[col++] = Calendar.formatDate((LocalDate)loanOdWorkingRegister.getMaturityDate());
        rows[col++] = Calendar.formatDate((LocalDate)loanOdWorkingRegister.getFirstDisbursementDate());
        rows[col++] = Calendar.formatDate((LocalDate)lenderWorkingRegisters.get(0).getStartDate());
        rows[col++] = (lenderWorkingRegisters.get(0).getStartDate() == null)? null : Calendar.formatDate((LocalDate)lenderWorkingRegisters.get(0).getStartDate().plusDays(1));
        rows[col++] = Calendar.formatDate((LocalDate)demandDate);
        rows[col++] = demandAmount.toValueString();
        rows[col++] = demandPrincipal.toValueString();
        rows[col++] = demandInterest.toValueString();
        rows[col++] = demandBalance.toValueString();
        rows[col++] = colenderAmount.toValueString();
        rows[col++] = colenderPrincipal.toValueString();
        rows[col++] = colenderInterest.toValueString();
        rows[col++] = colenderBalance.toValueString();
        rows[col++] = lenderAmount.toValueString();
        rows[col++] = lenderPrincipal.toValueString();
        rows[col++] = lenderInterest.toValueString();
        rows[col++] = lenderBalance.toValueString();
        results.add(new DefaultFieldSet (rows));
}