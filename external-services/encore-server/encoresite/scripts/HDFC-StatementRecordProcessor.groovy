import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.apache.commons.lang3.StringUtils;

import com.sensei.encore.basedomain.model.money.Money;
import com.sensei.encore.basedomain.model.account.AccountEntry.AccountEntryType;
import com.sensei.encore.application.dto.ReconBankAccountEntryDto;
import com.sensei.encore.basedomain.model.account.ReconEntryStatus;

if(cells.size() == 2) { // this could be a accountNumber entry
  for(String cell : cells) {
    if(cell.startsWith("Account No :")) {
	  def temp = cell.substring(12);
	  accountNumber = temp.substring(0, temp.indexOf(" "));
	  return;
	}
  }
} else if(cells.size() == 7) { // this could be a transaction entry
  entry = new ReconBankAccountEntryDto();
  def transactionDateTemp = cells.get(0);
  def valueDateTemp = cells.get(3);
  // create a formater 
  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy"); 
  try {
    def transactionDate = LocalDate.parse(transactionDateTemp, formatter);
    def valueDate = LocalDate.parse(valueDateTemp, formatter);
	entry.setTransactionDate(transactionDate);
	entry.setValueDate(valueDate);
  }  catch(DateTimeParseException e) {
    logger.error("Failed to parse date fields:" + valueDateTemp + "," + transactionDateTemp, e);
	entry = null;
    return;
  }
  def withdrawalAmount = (StringUtils.isEmpty(cells.get(4))) ? null : new Money(cells.get(4), currencyCode);
  def depositAmount = (StringUtils.isEmpty(cells.get(5))) ? null : new Money(cells.get(5), currencyCode);
  def balanceAmount = (StringUtils.isEmpty(cells.get(6))) ? null : new Money(cells.get(6), currencyCode);
  entry.setNarration(cells.get(1));
  entry.setReference(cells.get(2));
  if(withdrawalAmount != null) {
	entry.setAmount(withdrawalAmount);
	entry.setAccountEntryType(AccountEntryType.DEBIT);
  } else {
	entry.setAmount(depositAmount);
	entry.setAccountEntryType(AccountEntryType.CREDIT);
  }
  entry.setBalanceAmount(balanceAmount);
  entry.setStatus(ReconEntryStatus.UNRECONCILED);
}