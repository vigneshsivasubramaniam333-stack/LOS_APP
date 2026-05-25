import com.sensei.encore.basedomain.model.account.AccountEntry
import com.sensei.encore.basedomain.model.account.AccountProfile
import com.sensei.encore.basedomain.model.account.AccountRepository
import com.sensei.encore.basedomain.service.AccountService
import com.sensei.encore.loandomain.model.LoanOdDisbursement
import com.sensei.encore.loandomain.model.LoanOdRepository
import org.apache.commons.lang3.RandomStringUtils

logger.info("AccountClosurePreCheckExecute Script.")
AccountRepository accountRepository = applicationContext.getBean(AccountRepository.class);
AccountService accountService = applicationContext.getBean(AccountService.class);
String accountId = accountProfile.getAccountId();
List<AccountEntry> accountEntries = new ArrayList<>();
List<AccountEntry> disbAccountEntries = accountRepository.findEntriesByReferencedAccountIdAndTransactionNames(accountId, List.of(LoanOdDisbursement.Disbursement));
if(disbAccountEntries == null || disbAccountEntries.isEmpty()) {
    logger.info("No Account entries found {}",accountId);
    return ;
}
for(AccountEntry accountEntry: disbAccountEntries) {
    AccountProfile profile =  accountRepository.findAccountProfile(accountEntry.getAccountId());
    logger.info("account profile {} {}",profile.getProductCode());
    if(profile.getProductCode().equals("3821050") && accountEntry.getAccountEntryType() == AccountEntry.AccountEntryType.CREDIT) {
        AccountEntry marginMoneyAccountEntry = new AccountEntry(accountEntry);
        marginMoneyAccountEntry.setAccountEntryType(AccountEntry.AccountEntryType.DEBIT)
        marginMoneyAccountEntry.setValueDate(valueDate);
        marginMoneyAccountEntry.setTransactionDate(transactionDate);
        marginMoneyAccountEntry.setTransactionId(transactionId +"-"+ RandomStringUtils.randomAlphanumeric(5));
        marginMoneyAccountEntry.setTransactionName("LoanClosure");
        marginMoneyAccountEntry.setDescription("LoanClosure a/c "+accountId);
        String advanceEmiAccountId = accountRepository.findAccountId(accountProfile.getBranchCode(), "Internal", "3821005", accountProfile.getCurrencyCode());
        if(advanceEmiAccountId != null) {
            AccountEntry advanceEmiEntry = new AccountEntry(marginMoneyAccountEntry);
            advanceEmiEntry.setAccountEntryType(AccountEntry.AccountEntryType.CREDIT);
            advanceEmiEntry.setAccountId(advanceEmiAccountId);
            accountEntries.add(advanceEmiEntry);
            accountEntries.add(marginMoneyAccountEntry);
        }
    }
}

logger.info("accountEntries.. {}", accountEntries);
accountService.postEntries(accountEntries);
