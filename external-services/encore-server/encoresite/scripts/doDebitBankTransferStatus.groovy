import org.json.JSONObject;
import com.sensei.encore.basedomain.model.bank.BankTransfer.BankTransferStatus;
import com.sensei.encore.basedomain.model.bank.BankTransfer;
import com.sensei.encore.basedomain.model.base.ProviderApiUsageLog;
import com.sensei.encore.loandomain.errors.LoanDomainErrorCodes;
import com.sensei.encore.loandomain.errors.LoanDomainException;

logger.info("{}:{}; update bank transfer status started:{}",response);

JSONObject json = new JSONObject(response);
if(json.has("custTxnRef")){
    bankTransfer=bankRepository.findBankTransferByCustomerTransactionRef(json.get("custTxnRef").toString());
    if(bankTransfer==null) {
            logger.error("In-Valid customer transaction reference");
            throw new LoanDomainException (LoanDomainErrorCodes.BANK_TRANSFER_ERROR, new Object[]{"In-Valid customer transaction reference"});    
        }
    
    bankTransfer.setStatus(BankTransferStatus.CANCELLED);
    bankTransfer.setReason(json.get("rejectedRemarks").toString());
}else {
	JSONObject paymentTransactionResp = (JSONObject)json.get("paymentTransactionResp");
	JSONObject msgHdr = (JSONObject)paymentTransactionResp.get("msgHdr");
	JSONObject msgBdy = (JSONObject)paymentTransactionResp.get("msgBdy");
	JSONObject paymentRes = (JSONObject)msgBdy.get("paymentRes");
	String custRef = paymentRes.get("custRef").toString();

	if(custRef!=null && !custRef.isEmpty()) {
		bankTransfer=bankRepository.findBankTransferByCustomerTransactionRef(custRef);
		if(bankTransfer==null) {
			logger.error("In-Valid customer transaction reference");
			throw new LoanDomainException (LoanDomainErrorCodes.BANK_TRANSFER_ERROR, new Object[]{"In-Valid customer transaction reference"});	
		}
		String rslt = msgHdr.get("rslt").toString();
		if(rslt.equalsIgnoreCase("OK")) {
			String status = paymentRes.get("status").toString();
			String errorCode = paymentRes.get("errorCode").toString();
			if(status.equalsIgnoreCase("S") && errorCode.equalsIgnoreCase("PAY000")) {
				bankTransfer.setStatus(BankTransferStatus.REQUESTED);
				bankTransfer.setCmsRef(paymentRes.get("cmsRef").toString());
				bankTransfer.setCoreRef(paymentRes.get("coreRef").toString());
			}else {
				bankTransfer.setStatus(BankTransferStatus.FAILURE);
			}
		}else {
			bankTransfer.setStatus(BankTransferStatus.FAILURE);
		}			
		bankTransfer.setReason(paymentRes.get("statusDesc").toString());
	}else{
		logger.error("customer transaction reference is null");
		throw new LoanDomainException (LoanDomainErrorCodes.BANK_TRANSFER_ERROR, new Object[]{"customer transaction reference is null"});
	}
}
apiUsageLog = baseRepository.findProviderApiUsageLogByRequestId(bankTransfer.getProvider(), "doDebit", bankTransfer.getCustTxnRef());
apiUsageLog.setResponse(response);
apiUsageLog.setReason(bankTransfer.getReason());
apiUsageLog.setStatus(bankTransfer.getStatus().name());
logger.info("{}:{}; update bank transfer status completed:{}",bankTransfer);
return; 


