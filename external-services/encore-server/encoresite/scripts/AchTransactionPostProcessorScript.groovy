import com.sensei.encore.basedomain.model.account.AchTransaction.AchTransactionStatus;
import com.sensei.encore.application.facade.PaymentGatewayService;
import com.sensei.encore.basedomain.model.bank.PGTransaction;
import com.sensei.encore.application.dto.TransactionSummaryWSDto;
import java.math.BigDecimal;
import com.sensei.encore.application.facade.CommunicationServiceFacade;
import com.sensei.encore.basedomain.model.account.AccountRepository;
import com.sensei.encore.basedomain.model.account.AccountHolder;
import com.sensei.encore.basedomain.model.customer.CustomerRepository;
import com.sensei.encore.basedomain.model.customer.Customer;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import java.text.SimpleDateFormat;

logger.info("Transactions to send a payment link .......... {}",transactions)
PaymentGatewayService paymentGatewayService = applicationContext.getBean(PaymentGatewayService.class);
CommunicationServiceFacade communicationService = applicationContext.getBean(CommunicationServiceFacade.class);
AccountRepository accountRepository = applicationContext.getBean(AccountRepository.class);
CustomerRepository customerRepository = applicationContext.getBean(CustomerRepository.class);

for(TransactionSummaryWSDto transaction: transactions) {
    if(!transaction.getParam1().equalsIgnoreCase("Failure")) {
        logger.info("Transaction status is {} of an accountId {}", transaction.getParam1(), transaction.getAccountId());
        continue;
    }
    String accountNum = transaction.getAccountId();
    String amount = transaction.getAmount1();
   
    PGTransaction pgTransaction = paymentGatewayService.sendPaymentLink(accountNum,new BigDecimal(amount));
    String paymentLink = pgTransaction.getPaylinkUrl();
    if(StringUtils.isBlank(paymentLink)) {
        logger.info("Inavalid PaymentLink {} of an accountId {}", paymentGatewayService, accountNum);
        continue;
    }
    paymentLink = paymentLink.replace("https://","");
    SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy");
    String dueDate = formatter.format(transaction.getValueDate());
    logger.info("Payment link {} of an account Id {}", pgTransaction.getPaylinkUrl(), accountNum);
    List<AccountHolder> accountHolders = accountRepository.findAccountHolders(accountNum);
    AccountHolder accountHolder = accountHolders.stream().filter(a -> a.getHolderNum() == 1).findFirst().orElse(null);
    Customer customer = customerRepository.findCustomer(accountHolder.getCustomerId());
    String message = null;
    String stateCode = customer.getContact().getStateCode();
    String phone = customer.getContact().getPhone1();
    if(stateCode.equalsIgnoreCase("GJ")) {
        message = "1107172854668933445::પ્રિય ગ્રાહક, અમે તમને જણાવતા ખેદ અનુભવીએ છીએ કે તમારા લોન એકાઉન્ટ નંબર "+accountNum+"ની ચુકવણી કરવા માટે ₹ "+amount+"/- તારીખની "+dueDate+"ની EMI બાઉન્સ થઈ ગઈ છે. મહેરબાની કરીને નોંધ કરો કે EMI ની ચુકવણીમાં વિલંબ તમારા CIBIL સ્કોરને પ્રતિકૂળ અસર કરી શકે છે, તાત્કાલિક ચુકવણી માટે કૃપા કરીને અહીં "+paymentLink+" ક્લિક કરો. પર્પલ ફાઇનાન્સ લિમિટેડ."
    }else if(stateCode.equalsIgnoreCase("MH")) {
        message = "1107172854629168272::प्रिय ग्राहक, आम्हाला कळवण्यास खेद होत आहे की तुमच्या कर्ज खाते क्रमांक "+accountNum +" ची परतफेड करण्यासाठी ₹ "+amount+"/- तारीख "+dueDate+" ची ईएमआय बाउन्स झाली आहे. कृपया लक्षात घ्या की EMI च्या परतफेडीत विलंब झाल्यास तुमच्या CIBIL स्कोअरवर विपरित परिणाम होऊ शकतो, कृपया त्वरित पैसे भरण्यासाठी येथे "+paymentLink+" क्लिक करा. पर्पल फायनान्स लिमिटेड."
    } else {
        message = "1107172854651765516::प्रिय ग्राहक, हमें आपको यह बताते हुए दुख हो रहा है कि आपके ऋण खाता संख्या "+accountNum+" को चुकाने के लिए दिनांक "+dueDate+" की ₹ "+amount+"/- की ईएमआई बाउंस हो गई है। कृपया ध्यान दें कि ईएमआई के पुनर्भुगतान में देरी से आपके सिबिल स्कोर पर प्रतिकूल प्रभाव पड़ सकता है, कृपया तत्काल भुगतान के लिए यहां "+paymentLink+" क्लिक करें। पर्पल फाइनेंस लिमिटेड."
    }
    communicationService.storeScheduledAlert("Sms", null, accountNum, customer.getCustomerId(), null, message, phone, null, null);
}
