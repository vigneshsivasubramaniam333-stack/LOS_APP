import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.apache.commons.lang3.StringUtils;

import com.sensei.encore.basedomain.model.money.Money;
import com.sensei.encore.basedomain.model.account.AccountEntry.AccountEntryType;
import com.sensei.encore.application.dto.ReconBankAccountEntryDto;
import com.sensei.encore.basedomain.model.account.ReconEntryStatus;

if(cells.size() == 4) { // this could be a accountNumber entry
  if(cells.get(1).trim().equals("Account Number")) {
	accountNumber = cells.get(2).substring(0, cells.get(2).indexOf('('));
	return;
  }
} else if(cells.size() == 10) { // this could be a transaction entry
  entry = new ReconBankAccountEntryDto();
  def transactionDateTemp = cells.get(3);
  def valueDateTemp = cells.get(2);
  // create a formater 
  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy"); 
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
  def withdrawalAmount = cells.get(6).trim().equals("0") ? null : new Money(cells.get(6), currencyCode);
  def depositAmount = cells.get(7).trim().equals("0") ? null : new Money(cells.get(7), currencyCode);
  def balanceAmount = (StringUtils.isEmpty(cells.get(8))) ? null : new Money(cells.get(8), currencyCode);
  entry.setNarration(cells.get(5));
  entry.setReference(cells.get(4));
  if(withdrawalAmount != null) {
	entry.setAmount(withdrawalAmount);
	entry.setAccountEntryType(AccountEntryType.DEBIT);
  } else {
	entry.setAmount(depositAmount);
	entry.setAccountEntryType(AccountEntryType.CREDIT);
  }
  entry.setBalance(balanceAmount);
  entry.setStatus(ReconEntryStatus.UNRECONCILED);
}