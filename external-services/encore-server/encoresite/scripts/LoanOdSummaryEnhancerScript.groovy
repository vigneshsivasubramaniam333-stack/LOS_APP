import com.sensei.encore.basedomain.model.bank.BankAccount
import com.sensei.encore.basedomain.model.customer.Customer
import com.sensei.encore.basedomain.model.customer.CustomerRepository
import com.sensei.encore.basedomain.model.task.TransactionSummary
import com.sensei.encore.loandomain.model.LoanOdRepository;
import com.sensei.encore.loandomain.model.LoanOdSummary;
import com.sensei.encore.loandomain.model.RepaymentType;
import com.sensei.encore.basedomain.model.customer.Contact;
import com.sensei.encore.basedomain.model.calendar.Tenure;
import com.sensei.encore.basedomain.model.money.Money;
import com.sensei.encore.loandomain.model.LoanOdDemand;
import com.sensei.encore.loandomain.operation.LoanOdCancellationCommand;
import com.sensei.encore.basedomain.model.calendar.Calendar;
import com.sensei.encore.basedomain.model.base.BaseRepository;
import com.sensei.encore.basedomain.model.account.AccountStatementEntry;
import com.sensei.encore.basedomain.model.account.AccountEntry;
import com.sensei.encore.basedomain.model.account.AccountEntry.AccountEntryType;
import com.sensei.encore.basedomain.model.base.ConfigurationProperty;
import com.sensei.encore.internaldomain.model.interest.InterestSettings;
import com.sensei.encore.loandomain.model.LoanOdWorkingRegister;
import com.sensei.encore.basedomain.model.account.AccountProfile;
import com.sensei.encore.loandomain.model.LoanOdProduct;
import com.sensei.encore.loandomain.model.LoanOdProfile;
import com.sensei.encore.loandomain.model.LoanOdDisbursement;
import com.sensei.encore.loandomain.model.LoanOdWriteOff;
import com.sensei.encore.internaldomain.model.fee.Fee
import com.sensei.encore.util.entity.OperationalStatus
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils

import java.text.DateFormat
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.time.*
import java.util.regex.Pattern;

BaseRepository baseRepository = applicationContext.getBean(BaseRepository.class);
LoanOdRepository loanOdRepository = applicationContext.getBean(LoanOdRepository.class);
CustomerRepository customerRepository = applicationContext.getBean(CustomerRepository.class);

LoanOdSummary loanOdSummary = computeLoanOdSummaryQuery.loanOdSummary;
LoanOdWorkingRegister loanOdWorkingRegister = computeLoanOdSummaryQuery.loanOdWorkingRegister;
LoanOdProfile loanOdProfile = computeLoanOdSummaryQuery.loanOdProfile;
AccountProfile accountProfile = computeLoanOdSummaryQuery.accountProfile;
LoanOdProduct loanOdProduct = computeLoanOdSummaryQuery.loanOdProduct;
String accountId = computeLoanOdSummaryQuery.accountId;

DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

Money zero = new Money(0, accountProfile.getCurrencyCode());
Money installmentAmountDue = zero;
Money installmentAmountPaid = zero;
Money interestComponentDue = zero;
Money interestComponentPaid = zero;
Money principalComponentDue = zero;
Money principalComponentPaid = zero;
Money penalChargeDue = zero;
Money penalChargePaid = zero;
Money bounceChargeDue = zero;
Money bounceChargePaid = zero;
Money otherReceivablesDue = zero;
Money otherReceivablesPaid = zero;
Money OtherPayablesDue = zero;
Money OtherPayablesPaid = zero;
Money currentDue = zero;
Money lastPaymentAmount = zero;
String lastPaymentInstrument = null;

Money totalOutstandingDues =  loanOdSummary.getTotalDemandDue().add(loanOdSummary.getTotalFeeDue());
Money totalOutstandingLoanDues = loanOdSummary.getTotalDemandDue();

List<LoanOdSummary> activeCustomerLoans = loanOdRepository.findLoansByCustomerIdAndOperationalStatus(loanOdSummary.getCustomerId1(), OperationalStatus.ACTIVE);
loanOdSummary.getCustomDisplayAttributes().put("activeLoanCount", activeCustomerLoans.size());

loanOdSummary.getCustomDisplayAttributes().put("loanClosureDate", "NA");
if(loanOdSummary.getOperationalStatus() == OperationalStatus.CLOSED) {
    loanOdSummary.getCustomDisplayAttributes().put("loanClosureDate", loanOdSummary.getClosedOnValueDate().format(dateFormat));
}


Customer customer = customerRepository.findCustomer(loanOdSummary.getCustomerId1());
BankAccount bankAccount1 = customer.getBankAccount1();
if(bankAccount1 != null)
    bankAccount1.setAccountNumber(maskAccountNumber(bankAccount1.getAccountNumber()));
loanOdSummary.getCustomDisplayAttributes().put("repaymentBank", bankAccount1);
if(loanOdSummary.getOperationalStatus() != OperationalStatus.ACTIVE)
    loanOdSummary.setPendingTenure(new Tenure(0, loanOdSummary.getPendingTenure().getUnit()));

List<TransactionSummary> transactions = loanOdSummary.getTransactions();
List<TransactionSummary> newTransactions = new ArrayList<>();
final List<String> transactionNameArray = Arrays.asList(RepaymentType.SCHEDULED_REPAYMENT.displayName(), RepaymentType.ADVANCE_REPAYMENT.displayName(), RepaymentType.PREPAYMENT.displayName(), RepaymentType.PRECLOSURE.displayName());
Money subventionFee = null;
Money totalRepaid = loanOdSummary.getTotalRepaid().add(loanOdSummary.getTotalFeeRepaid());
Money principalNotDue = loanOdSummary.getPrincipalNotDue();

if(loanOdSummary.getSecurityDepositInstallments() != null) {
    totalRepaid = totalRepaid.subtract(loanOdSummary.getEquatedInstallment().multiply(loanOdSummary.getSecurityDepositInstallments()));
    principalNotDue = principalNotDue.subtract(loanOdSummary.getEquatedInstallment().multiply(loanOdSummary.getSecurityDepositInstallments()));
}
for (TransactionSummary transaction : transactions) {
    if (transaction.getTransactionName().equals(RepaymentType.PRECLOSURE.displayName())) {
        loanOdSummary.setDisplayStatus("Preclosed");
    }
    if (transaction.getTransactionName().equals(Fee.FeeChargeTransaction) && "Subvention Fee".equals(transaction.getParam1())) {
        subventionFee = transaction.getAmount1();
        loanOdSummary.getCustomDisplayAttributes().put("SubventionFee", subventionFee.toValueString());
        totalRepaid = totalRepaid.subtract(subventionFee);
        if("false".equals(transaction.getParam3()))
            totalOutstandingDues = totalOutstandingDues.subtract(subventionFee);
    }
    if ((transaction.getTransactionName().equals(RepaymentType.SCHEDULED_REPAYMENT.displayName()) ||
            transaction.getTransactionName().equals(RepaymentType.PRECLOSURE.displayName())) &&
            transaction.getPart8() != null && transaction.getPart8().getMagnitude().compareTo(BigDecimal.ZERO) > 0) {
        Money excessPayment = transaction.getPart8();
        transaction.setAmount1(transaction.getAmount1().subtract(excessPayment));
        TransactionSummary extraTransaction = new TransactionSummary(transaction);
        extraTransaction.setTransactionName("Excess Payment");
        extraTransaction.setAmount1(excessPayment);
        newTransactions.add(transaction);
        newTransactions.add(extraTransaction);
    } else {
        newTransactions.add(transaction);
    }
}
loanOdSummary.setTransactions(newTransactions);
totalOutstandingDues = totalOutstandingDues.add(principalNotDue);
totalOutstandingLoanDues = totalOutstandingLoanDues.add(principalNotDue);

if(totalRepaid.isLessThan(zero))
    totalRepaid = zero;
if(principalNotDue.isLessThan(zero))
    principalNotDue = zero;
if(totalOutstandingDues.isLessThan(zero))
    totalOutstandingDues = zero;
if(totalOutstandingLoanDues.isLessThan(zero))
    totalOutstandingLoanDues = zero;
loanOdSummary.getCustomDisplayAttributes().put("totalRepaid", totalRepaid.toValueString());
loanOdSummary.getCustomDisplayAttributes().put("principalNotDue", principalNotDue.toValueString());
loanOdSummary.getCustomDisplayAttributes().put("totalOutstandingDues", totalOutstandingDues.toValueString());
loanOdSummary.getCustomDisplayAttributes().put("totalOutstandingLoanDues", totalOutstandingLoanDues.toValueString());

loanOdSummary.getCustomDisplayAttributes().put("tenure", new Tenure(loanOdSummary.getTenure().getMagnitude(), loanOdSummary.getTenure().getUnit()));
if(loanOdSummary.getOperationalStatus() == OperationalStatus.CLOSED)
    loanOdSummary.getCustomDisplayAttributes().put("maturityDate", loanOdSummary.getClosedOnValueDate().format(dateFormat));
else
    loanOdSummary.getCustomDisplayAttributes().put("maturityDate", loanOdSummary.getMaturityDate().format(dateFormat));
loanOdSummary.getCustomDisplayAttributes().put("pendingTenure", new Tenure(loanOdSummary.getPendingTenure().getMagnitude(), loanOdSummary.getPendingTenure().getUnit()));
if (loanOdSummary.getSecurityDepositInstallments() != null) {
    ((Tenure)loanOdSummary.getCustomDisplayAttributes().get("tenure")).setMagnitude(loanOdSummary.getTenure().getMagnitude() - loanOdSummary.getSecurityDepositInstallments());
    Tenure deltaTenure = new Tenure(loanOdSummary.getSecurityDepositInstallments(), loanOdSummary.getTenure().getUnit()).negate();
    if(loanOdSummary.getOperationalStatus() != OperationalStatus.CLOSED)
        loanOdSummary.getCustomDisplayAttributes().put("maturityDate", computeLoanOdSummaryQuery.calendar.moveByTenure(loanOdSummary.getMaturityDate(), deltaTenure).format(dateFormat));
    if(loanOdSummary.getOperationalStatus() == OperationalStatus.ACTIVE)
        ((Tenure)loanOdSummary.getCustomDisplayAttributes().get("pendingTenure")).setMagnitude(loanOdSummary.getPendingTenure().getMagnitude() - loanOdSummary.getSecurityDepositInstallments());
}
if (loanOdSummary.getDaysPastDue() != null && loanOdSummary.getDaysPastDue() > 0) {
    loanOdSummary.setDisplayStatus("Active Due");
}
Contact contact1 = loanOdSummary.getContact1();
if (contact1 != null) {
    contact1.setEmail(maskEmail(contact1.getEmail()));
    contact1.setPhone1(maskPhone(contact1.getPhone1()));
    contact1.setPhone2(maskPhone(contact1.getPhone2()));
}
Contact contact2 = loanOdSummary.getContact2();
if (contact2 != null) {
    contact2.setEmail(maskEmail(contact2.getEmail()));
    contact2.setPhone1(maskPhone(contact2.getPhone1()));
    contact2.setPhone2(maskPhone(contact2.getPhone2()));
}
Contact contact3 = loanOdSummary.getContact3();
if (contact3 != null) {
    contact3.setEmail(maskEmail(contact3.getEmail()));
    contact3.setPhone1(maskPhone(contact3.getPhone1()));
    contact3.setPhone2(maskPhone(contact3.getPhone2()));
}

ConfigurationProperty property = baseRepository.findProperty("Loans", "SOAFormat");
String format = "Instalment";
if (property != null && !property.getPropertyValue().isEmpty())
    format = property.getPropertyValue();
property = baseRepository.findProperty("Loans", "SOAShowAbsBalance");
boolean showAbsBalance = property == null ? true : property.getPropertyValueAsBoolean();
property = baseRepository.findProperty("Loans", "SOAShowLoanBalance");
boolean showLoanBalance = property == null ? false : property.getPropertyValueAsBoolean();
LocalDate maturityDate = loanOdWorkingRegister.getMaturityRunDate();
List<AccountStatementEntry> accountEntries = new ArrayList<>();
Money runningBalance = zero;
InterestSettings settings = loanOdProduct.getInterestSettings();
int installmentNum = 0;
int installmentCount = loanOdSummary.getTenure().getMagnitude() - loanOdSummary.getSecurityDepositInstallments();
List<JFSAccountStatementEntry> jfsAccountEntries = new ArrayList<>();
Money jfsRunningBalance = zero;
if (loanOdSummary.getSecurityDepositInstallments() != null) {
    Money securityDeposit = loanOdSummary.getEquatedInstallment().multiply(loanOdSummary.getSecurityDepositInstallments());
    loanOdSummary.getCustomDisplayAttributes().put("loanAmount", loanOdSummary.getAmount().subtract(securityDeposit));
    jfsRunningBalance = jfsRunningBalance.add(securityDeposit);
    JFSAccountStatementEntry jFSAccountStatementEntry1 = new JFSAccountStatementEntry(loanOdSummary.getAccountOpenDate().format(dateFormat), "Advance EMI",
            "Adv EMI Recd From Customer- Due", null, securityDeposit.toValueString(), null, null, null, jfsRunningBalance.toValueString());
    jfsAccountEntries.add(jFSAccountStatementEntry1);
    jfsRunningBalance = jfsRunningBalance.subtract(securityDeposit);
    JFSAccountStatementEntry jFSAccountStatementEntry2 = new JFSAccountStatementEntry(loanOdSummary.getAccountOpenDate().format(dateFormat), "Advance EMI",
            "Adv EMI Received", null, null, securityDeposit.toValueString(), null, null, jfsRunningBalance.toValueString());
    jfsAccountEntries.add(jFSAccountStatementEntry2);
} else {
    loanOdSummary.getCustomDisplayAttributes().put("loanAmount", loanOdSummary.getAmount());
}
for (TransactionSummary t : transactions) {
    if ("Subvention Fee".equals(t.getParam1())) {
        continue;
    }
    /* if (t.getTransactionName().equals(LoanOdDisbursement.Disbursement)) {
    // Do not add disbursement to statement
    } */
    if (t.getTransactionName().equals(LoanOdDemand.Demand)) {
        boolean bpiDemand = Boolean.parseBoolean(t.getParam1());
        boolean scheduledDemand = Boolean.parseBoolean(t.getStatus());
        boolean postMaturityDemand = (maturityDate != null && Calendar.compareTo(t.getValueDate(), maturityDate) > 0);
        boolean installmentDemand = (!bpiDemand && !postMaturityDemand && scheduledDemand);
        if (installmentDemand)
            installmentNum++;
        String transactionName = "Due for Instalment No " + t.getSequenceNum();
        if (format.contains("Instalment") && installmentDemand)
            transactionName = "Due for Instalment No " + installmentNum;
        else if (format.contains("Interest") && (bpiDemand || postMaturityDemand))
            transactionName = "Interest";
        Money amount = t.getAmount1();
        installmentAmountDue = installmentAmountDue.add(amount);
        principalComponentDue = principalComponentDue.add(t.getPart2());
        interestComponentDue = interestComponentDue.add(t.getPart1());
        currentDue = currentDue.add(amount);
        jfsRunningBalance = jfsRunningBalance.add(amount);
        JFSAccountStatementEntry demandEntry = new JFSAccountStatementEntry(t.getValueDate().format(dateFormat), "Installment Due", transactionName,
                null, amount.toValueString(), null, null, null, jfsRunningBalance.toValueString());
        jfsAccountEntries.add(demandEntry);
    } else if (Arrays.asList(RepaymentType.SCHEDULED_REPAYMENT.displayName(), RepaymentType.PRECLOSURE.displayName(), RepaymentType.PREPAYMENT.displayName(), RepaymentType.ADVANCE_REPAYMENT.displayName(),
            LoanOdWriteOff.WriteOffTransactionType.SETTLEMENT.name(), LoanOdWriteOff.WriteOffTransactionType.FULL_SETTLEMENT.name()).contains(t.getTransactionName())) {
        String transactionType = "Instalment Repaid";
        String particulars = "Amount received for Instalment No " + t.getSequenceNum();
        if(t.getTransactionName().equals(RepaymentType.PREPAYMENT.displayName())) {
            transactionType = "Excess Receipt";
            particulars = "Excess received to be adjusted towards next instalment";
        } else if(t.getTransactionName().equals(RepaymentType.PRECLOSURE.displayName())) {
            transactionType = "Pre Closure";
            particulars = "Received towards loan closure";
        }
        Money amount = t.getAmount1();
        String reference = t.getReference();
        if(!StringUtils.isEmpty(reference)) {
            m = Pattern.compile("securityDeposit:"+Money.MoneyPattern).matcher(reference);
            Money usedSecurityDeposit = (m.find()) ? new Money (m.group(1), accountProfile.getCurrencyCode()) : zero;
            if(usedSecurityDeposit.isPositive()) {
                amount = amount.subtract(usedSecurityDeposit);
            }
        }
        if(amount.isGreaterThan(zero)) {
            lastPaymentAmount = amount;
            lastPaymentInstrument = t.getInstrument().displayName();
        }
        installmentAmountPaid = installmentAmountPaid.add(amount);
        principalComponentPaid = principalComponentPaid.add(t.getPart2());
        interestComponentPaid = interestComponentPaid.add(t.getPart1());
        currentDue = currentDue.subtract(amount);

        jfsRunningBalance = jfsRunningBalance.subtract(amount);
        JFSAccountStatementEntry repaymentEntry = new JFSAccountStatementEntry(t.getValueDate().format(dateFormat), transactionType, particulars,
                "Cleared", null, amount.toValueString(), null, null, jfsRunningBalance.toValueString());
        jfsAccountEntries.add(repaymentEntry);
    } else if (t.getTransactionName().equals(Fee.FeeChargeTransaction)) {
        String particulars = t.getParam1();
        t.setDescription(t.getParam1());
        if (t.getParam1().equals("MBC")) {
            t.setDescription("Mandate Bounce Charge");
            particulars = "Installment Bounced";
        }
        Money amount = t.getAmount1();
        if(t.getParam1().equals("MBC")) {
            bounceChargeDue = bounceChargeDue.add(amount);
        } else if(t.getParam1().equals("LateFee")) {
            penalChargeDue = penalChargeDue.add(amount);
        } else {
            otherReceivablesDue = otherReceivablesDue.add(amount);
        }
        currentDue = currentDue.add(amount);

        jfsRunningBalance = jfsRunningBalance.add(amount);
        JFSAccountStatementEntry feeChargeEntry = new JFSAccountStatementEntry(t.getValueDate().format(dateFormat), t.getDescription(), particulars,
                null, null, null, amount.toValueString(), null, jfsRunningBalance.toValueString());
        jfsAccountEntries.add(feeChargeEntry);
    } else if (t.getTransactionName().equals(Fee.FeePaymentTransaction)) {
        t.setDescription(t.getParam1());
        if (t.getParam1().equals("MBC"))
            t.setDescription("Mandate Bounce Charge");
        Money amount = t.getAmount1();
        if(t.getParam1().equals("MBC")) {
            bounceChargePaid = bounceChargePaid.add(amount);
        } else if(t.getParam1().equals("LateFee")) {
            penalChargePaid = penalChargePaid.add(amount);
        } else {
            otherReceivablesPaid = otherReceivablesPaid.add(amount);
        }
        currentDue = currentDue.subtract(amount);
        if(amount.isGreaterThan(zero)) {
            lastPaymentAmount = amount;
            lastPaymentInstrument = t.getInstrument().displayName();
        }
        jfsRunningBalance = jfsRunningBalance.subtract(amount);
        JFSAccountStatementEntry feePaymentEntry = new JFSAccountStatementEntry(t.getValueDate().format(dateFormat), t.getDescription(), "Amount received for " + t.getDescription(),
                null, null, null, null, amount.toValueString(), jfsRunningBalance.toValueString());
        jfsAccountEntries.add(feePaymentEntry);
    } else if (t.getTransactionName().equals(LoanOdCancellationCommand.TransactionName)) {
        Money amount = runningBalance;
        jfsRunningBalance = zero;
        JFSAccountStatementEntry feePaymentEntry = new JFSAccountStatementEntry(t.getValueDate().format(dateFormat), "Loan Cancellation", "Loan Cancelled",
                null, null, null, null, null, jfsRunningBalance.toValueString());
        jfsAccountEntries.add(feePaymentEntry);
    }
}
loanOdSummary.getCustomDisplayAttributes().put("jfsAccountEntries", new JRBeanCollectionDataSource(jfsAccountEntries));

loanOdSummary.getCustomDisplayAttributes().put("installmentAmountDue", installmentAmountDue);
loanOdSummary.getCustomDisplayAttributes().put("installmentAmountPaid", installmentAmountPaid);
loanOdSummary.getCustomDisplayAttributes().put("installmentAmountOverdue", installmentAmountDue.subtract(installmentAmountPaid));
loanOdSummary.getCustomDisplayAttributes().put("interestComponentDue", interestComponentDue);
loanOdSummary.getCustomDisplayAttributes().put("interestComponentPaid", interestComponentPaid);
loanOdSummary.getCustomDisplayAttributes().put("interestComponentOverdue", interestComponentDue.subtract(interestComponentPaid));
loanOdSummary.getCustomDisplayAttributes().put("principalComponentDue", principalComponentDue);
loanOdSummary.getCustomDisplayAttributes().put("principalComponentPaid", principalComponentPaid);
loanOdSummary.getCustomDisplayAttributes().put("principalComponentOverdue", principalComponentDue.subtract(principalComponentPaid));
loanOdSummary.getCustomDisplayAttributes().put("penalChargeDue", penalChargeDue);
loanOdSummary.getCustomDisplayAttributes().put("penalChargePaid", penalChargePaid);
loanOdSummary.getCustomDisplayAttributes().put("penalChargeOverdue", penalChargeDue.subtract(penalChargePaid));
loanOdSummary.getCustomDisplayAttributes().put("bounceChargeDue", bounceChargeDue);
loanOdSummary.getCustomDisplayAttributes().put("bounceChargePaid", bounceChargePaid);
loanOdSummary.getCustomDisplayAttributes().put("bounceChargeOverdue", bounceChargeDue.subtract(bounceChargePaid));
loanOdSummary.getCustomDisplayAttributes().put("otherReceivablesDue", otherReceivablesDue);
loanOdSummary.getCustomDisplayAttributes().put("otherReceivablesPaid", otherReceivablesPaid);
loanOdSummary.getCustomDisplayAttributes().put("otherReceivablesOverdue", otherReceivablesDue.subtract(otherReceivablesPaid));
loanOdSummary.getCustomDisplayAttributes().put("OtherPayablesDue", OtherPayablesDue);
loanOdSummary.getCustomDisplayAttributes().put("OtherPayablesPaid", OtherPayablesPaid);
loanOdSummary.getCustomDisplayAttributes().put("currentDue", currentDue);
loanOdSummary.getCustomDisplayAttributes().put("lastPaymentAmount", lastPaymentAmount);
loanOdSummary.getCustomDisplayAttributes().put("lastPaymentInstrument", lastPaymentInstrument);


static String maskEmail(String input) {
    if (input == null || input.length() < 3) return input;
    StringBuilder sb = new StringBuilder(input);
    for (int i = 0; i < sb.length(); ++i) {
        if(sb[i] == '@') break;
        if(i%2 == 1)
            sb.setCharAt(i, '*' as char);
    }
    return sb.toString();
}

static String maskPhone(String input) {
    if (input == null || input.length() < 4) return input;
    StringBuilder sb = new StringBuilder(input);
    for (int i = 2; i < sb.length() - 2; ++i) {
        sb.setCharAt(i, '*' as char);
    }
    return sb.toString();
}

static String maskAccountNumber(String accountNumber) {
    if (accountNumber == null || accountNumber.length() < 4) return accountNumber;
    StringBuilder sb = new StringBuilder(accountNumber);
    for (int i = 0; i < sb.length() - 4; ++i) {
        sb.setCharAt(i, '*' as char);
    }
    return sb.toString();
}
class JFSAccountStatementEntry {
    private String valueDate;
    private String transactionType;
    private String particulars;
    private String transactionStatus;
    private String debit;
    private String credit;
    private String due;
    private String paid;
    private String balance;

    JFSAccountStatementEntry(String valueDate, String transactionType, String particulars, String transactionStatus, String debit, String credit, String due, String paid, String balance) {
        this.valueDate = valueDate
        this.transactionType = transactionType
        this.particulars = particulars
        this.transactionStatus = transactionStatus
        this.debit = debit
        this.credit = credit
        this.due = due
        this.paid = paid
        this.balance = balance
    }

    String getValueDate() {
        return valueDate
    }

    void setValueDate(String valueDate) {
        this.valueDate = valueDate
    }

    String getTransactionType() {
        return transactionType
    }

    void setTransactionType(String transactionType) {
        this.transactionType = transactionType
    }

    String getParticulars() {
        return particulars
    }

    void setParticulars(String particulars) {
        this.particulars = particulars
    }

    String getTransactionStatus() {
        return transactionStatus
    }

    void setTransactionStatus(String transactionStatus) {
        this.transactionStatus = transactionStatus
    }

    String getDebit() {
        return debit
    }

    void setDebit(String debit) {
        this.debit = debit
    }

    String getCredit() {
        return credit
    }

    void setCredit(String credit) {
        this.credit = credit
    }

    String getDue() {
        return due
    }

    void setDue(String due) {
        this.due = due
    }

    String getPaid() {
        return paid
    }

    void setPaid(String paid) {
        this.paid = paid
    }

    String getBalance() {
        return balance
    }

    void setBalance(String balance) {
        this.balance = balance
    }

    @Override
    public String toString() {
        return "JFSAccountStatementEntry{" +
                "valueDate='" + valueDate + '\'' +
                ", transactionType='" + transactionType + '\'' +
                ", particulars='" + particulars + '\'' +
                ", transactionStatus='" + transactionStatus + '\'' +
                ", debit='" + debit + '\'' +
                ", credit='" + credit + '\'' +
                ", due='" + due + '\'' +
                ", paid='" + paid + '\'' +
                ", balance='" + balance + '\'' +
                '}';
    }
}

