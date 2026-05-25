import com.sensei.encore.basedomain.model.account.AccountIdGenerationRule;
import com.sensei.encore.basedomain.service.AccountService;
import com.sensei.encore.basedomain.model.base.BaseRepository;

BaseRepository baseRepository = applicationContext.getBean(BaseRepository.class);
AccountService accountService = applicationContext.getBean(AccountService.class);

if(fee.getFeeCategory().toUpperCase().startsWith("PROCESSING FEES")){
    String ruleCode = baseRepository.findProperty("Loans", "FeeInvoiceNumberRuleCode").getPropertyValueAsString();
    Map<AccountIdGenerationRule.AccountIdPartType, Object> settings = new EnumMap<>(AccountIdGenerationRule.AccountIdPartType.class);
    settings.put(AccountIdGenerationRule.AccountIdPartType.FinancialYear, fee.getOriginalTransactionDate());
    String invoiceNumber = accountService.generateAccountId(ruleCode, settings);
    fee.setReference(fee.getReference() +"|invoiceNum:"+invoiceNumber);
}

