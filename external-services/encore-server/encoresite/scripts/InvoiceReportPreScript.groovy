import com.sensei.encore.application.facade.PurchaseSaleServiceFacade;
import com.sensei.encore.basedomain.model.bank.BankRepository;
import com.sensei.encore.basedomain.model.customer.CustomerRepository;
import com.sensei.encore.application.dto.PurchaseSaleTransactionDto;
import com.sensei.encore.basedomain.model.bank.Bank;
import com.sensei.encore.basedomain.model.customer.Customer;

import java.util.*;
import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;

LocalDateTime startTime=LocalDateTime.now();
logger.info("start Time ............. {}",startTime);

PurchaseSaleServiceFacade purchaseSaleService = applicationContext.getBean(PurchaseSaleServiceFacade.class);
BankRepository bankRepository = applicationContext.getBean(BankRepository.class);
CustomerRepository customerRepository = applicationContext.getBean(CustomerRepository.class);
PurchaseSaleTransactionDto purchaseSaleTransactionDto = purchaseSaleService.getPurchaseSaleTransaction(params.get("transactionNumber"));
Bank bank = bankRepository.findBank();
Customer customer = customerRepository.findCustomer(purchaseSaleTransactionDto.getCustomerId());
params.put("purchaseSaleTransaction", purchaseSaleTransactionDto);
params.put("buyerName", bank.getRegisteredBankName());
params.put("sellerName", customer.getCustomerName().toString());
params.put("buyerContact", bank.getContact());
params.put("sellerContact", customer.getContact());
params.put("buyerGstin", bank.getGstin());
params.put("sellerGstin", customer.getGstin());
params.put("buyerPan", bank.getPan());
params.put("sellerPan", customer.getPan());

params.put("__REQUIRE_EMPTY_DATASOURCE__", Boolean.TRUE);

LocalDateTime endTime=LocalDateTime.now();
logger.info("end Time ............. {}",endTime);
logger.info("Difference between in milli second ......... {}",ChronoUnit.MILLIS.between(startTime,endTime))		
