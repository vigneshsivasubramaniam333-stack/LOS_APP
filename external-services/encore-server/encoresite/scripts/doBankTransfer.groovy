import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.springframework.web.bind.annotation.*;
import org.apache.commons.io.IOUtils;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.sensei.encore.basedomain.model.bank.BankAccount;
import com.sensei.encore.basedomain.model.money.Instrument;
import com.sensei.encore.basedomain.model.bank.BankTransfer.BankTransferStatus;
import com.sensei.encore.basedomain.model.base.ProviderApiUsageLog;
import com.sensei.encore.loandomain.errors.LoanDomainErrorCodes;
import com.sensei.encore.loandomain.errors.LoanDomainException;

if (bankTransfer.getStatus() == BankTransferStatus.PENDING) {

	apiUsageLog =  new ProviderApiUsageLog(bankTransfer.getProvider(), "doDebit", bankTransfer.getCustTxnRef(), "", "", bankTransfer.getStatus().name(), "LoanAccount", bankTransfer.getEntityId(), ZonedDateTime.now(), null);
	logger.info("{}:{}; status bank transfer started:{}");

	String custTxnRef = bankTransfer.getCustTxnRef();
	String beneAccNo = bankTransfer.getBankAccount().getAccountNumber();
	String beneName = bankTransfer.getBankAccount().getAccountHolderName();
	String beneAddr1 = "line1";
	String beneAddr2 = "";
	String ifsc = bankTransfer.getBankAccount().getIfscCode();
	String tranCcy = "000";
	String tranAmount = bankTransfer.getAmount().getMagnitude().toString();
	String remitInfo1 = "";
	String remitInfo2 = "";
	String paymentType = bankTransfer.getInstrument();
	String beneAccType = "SA";
	String beneMail = bankTransfer.getContact().getEmail();
	String beneMobile ="91"+bankTransfer.getContact().getPhone1();
	String timestamp = bankTransfer.getTimeStampStr();
	String valueDate = bankTransfer.getTransactionDateStr();

	String test = "{\"paymentTransactionReq\":{\"msgHdr\":{\"msgId\":\"847586859632\",\"cnvId\":\"\",\"extRefId\":\"\",\"bizObjId\":\"\",\"appId\":\"\",\"timestamp\":\""+timestamp+"\"},\"msgBdy\":{\"paymentReq\":{\"custTxnRef\":\""+custTxnRef+"\",\"beneAccNo\":\""+beneAccNo+"\",\"beneName\":\""+beneName+"\",\"beneAddr1\":\""+beneAddr1+"\",\"beneAddr2\":\"\",\"ifsc\":\""+ifsc+"\",\"valueDate\":\""+valueDate+"\",\"tranCcy\":\"INR\",\"tranAmount\":\""+tranAmount+"\",\"purposeCode\":\"Loan Disbursement\",\"remitInfo1\":\"\",\"remitInfo2\":\"\",\"clientCode\":\"SAMUAT\",\"paymentType\":\""+paymentType+"\",\"beneAccType\":\""+beneAccType+"\",\"remarks\":\"\",\"beneMail\":\""+beneMail+"\",\"beneMobile\":\""+beneMobile+"\"}}}}";
	logger.info("Status bank transfer processing:{}",test);
	String urlParameters  = "requestJSON="+test+"&requesterCode=REQTR001&userName=Suresh Kumar R&userId=usr087&straightthroughprocessFlag=N";
	byte[] postData = urlParameters.getBytes( StandardCharsets.UTF_8 );
	request = "http://uatsambtrn.storyboarderp.com/EMSV3API/api/SamBTranAPIRequester/IDFCFirstDoDebit";
	apiUsageLog.setRequest(request);
	URL url = new URL(request);
	HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	connection.setRequestMethod("POST");
	connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
	connection.setRequestProperty("SamBTrn-ApiKey", "6a3b177b-e8fa-4e73-99f3-a55e3bfb1025");
	connection.setDoOutput(true);

	DataOutputStream requestWriter = new DataOutputStream(connection.getOutputStream());
	requestWriter.write(postData);
	requestWriter.close();
	InputStream is = connection.getInputStream();
	response = IOUtils.toString(is);
	String reason = "";
	logger.info("Response received:{}",response);
	if (response == null) {
		status = BankTransferStatus.FAILURE;
		reason = "No Response";
		bankTransfer.setReason(reason);
		bankTransfer.setStatus(status);
		apiUsageLog.setStatus(status);
		apiUsageLog.setReason(reason);
		return;
	}
	apiUsageLog.setResponse(response);
	JSONObject json = new JSONObject(response);

	if (json.get("paymentTransactionResp").toString().equalsIgnoreCase('null')) {
		status = BankTransferStatus.SUBMITTED;
		reason = json.get("message").toString();
		logger.info("Reason:{}",reason);
	}else{
		logger.info("Response received reason else:{}");
		JSONObject paymentTransactionResp =(JSONObject) json.get("paymentTransactionResp");
		JSONObject msgHdr =(JSONObject)paymentTransactionResp.get("msgHdr");
		JSONObject msgBdy =(JSONObject)paymentTransactionResp.get("msgBdy");
		JSONObject paymentRes =(JSONObject)msgBdy.get("paymentRes");
		String result = msgHdr.get("rslt").toString();
		status = bankTransfer.getInstrument() == Instrument.NEFT? BankTransferStatus.REQUESTED:BankTransferStatus.SUCCESS;
		if (result.equals("ERROR")) {
			JSONObject error =(JSONObject)msgHdr.get("error");
			reason = error.get("rsn")!=null?error.get("rsn").toString():"";
			status = BankTransferStatus.FAILURE;
		}else{
			status = BankTransferStatus.REQUESTED;
			bankTransfer.setCoreRef(paymentRes.get("coreRef").toString());
			bankTransfer.setCmsRef(paymentRes.get("cmsRef").toString());
			bankTransfer.setCustRef(paymentRes.get("custRef").toString());
		}	
	}
	bankTransfer.setReason(reason);
	bankTransfer.setStatus(status);
	apiUsageLog.setStatus(status);
	apiUsageLog.setReason(reason);
	logger.info("{}:{}; status bank transfer completed:{}",response);
} else if (bankTransfer.getStatus() == BankTransferStatus.REQUESTED) {
	apiUsageLog =  new ProviderApiUsageLog(bankTransfer.getProvider(), "doDebitStatusEnquiry", bankTransfer.getCustTxnRef(), "", "", bankTransfer.getStatus().name(), "LoanAccount", bankTransfer.getEntityId(), ZonedDateTime.now(), null);
	logger.info("{}:{}; status bank transfer status enquiry started:{}");

	String custRef = bankTransfer.getCustTxnRef();
	String bankRef = bankTransfer.getCoreRef();
	String cmsRef = bankTransfer.getCmsRef();
	String timestamp = bankTransfer.getTimeStampStr();

	String test = "{\"PaymentsDebitTransactionInqReq\":{\"msgHdr\":{\"msgId\":\"847586859632\",\"cnvId\":\"\",\"extRefId\":\"\",\"bizObjId\":\"\",\"appId\":\"\",\"timestamp\":\""+timestamp+"\"},\"msgBdy\":{\"PymntsDbtTrnsctnInqReq\":{\"custRef\":\"" + custRef + "\",\"bankRef\":\"" + bankRef + "\",\"cmsRef\":\"" + cmsRef + "\",\"instNo\":\"\",\"clientCode\":\"SAMUAT\"}}}}";
	String urlParameters  = "requestJSON="+test+"&requesterCode=REQTR001&userName=Suresh Kumar R&userId=usr087&straightthroughprocessFlag=Y";
	logger.info("{}:{}; status bank transfer status enquiry processing:{}",test);
	byte[] postData = urlParameters.getBytes( StandardCharsets.UTF_8 );
	request = "http://uatsambtrn.storyboarderp.com/EMSV3API/api/SamBTranAPIRequester/IDFCFirstDoDebitStatusEnquiry";
	apiUsageLog.setRequest(request);
	URL url = new URL(request);
	HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	connection.setRequestMethod("POST");
	connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
	connection.setRequestProperty("SamBTrn-ApiKey", "6a3b177b-e8fa-4e73-99f3-a55e3bfb1025");
	connection.setDoOutput(true);

	DataOutputStream requestWriter = new DataOutputStream(connection.getOutputStream());
	requestWriter.write(postData);
	requestWriter.close();
	InputStream is = connection.getInputStream();
	response = IOUtils.toString(is);
	apiUsageLog.setResponse(response);
	logger.info("{}:{}; status bank transfer status enquiry processing...:{}",response);
	JSONObject json = new JSONObject(response);
	JSONObject paymentTransactionResp =(JSONObject) json.get("PaymentsDebitTransactionInqRes");
	JSONObject msgHdr =(JSONObject)paymentTransactionResp.get("msgHdr");
	String result = msgHdr.get("rslt").toString();
	status = BankTransferStatus.SUCCESS;
	String reason = "";
	if (result.equals("ERROR")) {
		JSONObject error =(JSONObject)msgHdr.get("error");
		reason = error.get("rsn")!=null? error.get("rsn").toString():"";
		status = BankTransferStatus.FAILURE;
	}else{
		JSONObject msgBdy =(JSONObject)paymentTransactionResp.get("msgBdy");
		JSONObject PymntsDbtTxnInqResp =(JSONObject)msgBdy.get("PymntsDbtTxnInqResp");
		String status =PymntsDbtTxnInqResp.get("status");
		String statusDesc =PymntsDbtTxnInqResp.get("statusDesc");
		String errorCode =PymntsDbtTxnInqResp.get("errorCode");
		if(status.equalsIgnoreCase("S") && errorCode.equalsIgnoreCase("ENQ000")){
			status = BankTransferStatus.SUCCESS;
				
		}else if(status.equalsIgnoreCase("F") && errorCode.equalsIgnoreCase("ENQ000")){
			status = BankTransferStatus.FAILURE;
			
		}else {
			status = BankTransferStatus.REQUESTED;
			
		}
		reason=statusDesc;	
	}
	bankTransfer.setStatus(status);
	bankTransfer.setReason(reason);
	apiUsageLog.setStatus(status);
	apiUsageLog.setReason(reason);
	logger.info("{}:{}; status bank transfer status enquiry completed:{}",response);
} else {
	response = "";
	status = bankTransfer.getStatus();
	apiUsageLog =  null;
	logger.info("{}:{}; status bank transfer status enquiry completed:{}",response);
}
return; 


