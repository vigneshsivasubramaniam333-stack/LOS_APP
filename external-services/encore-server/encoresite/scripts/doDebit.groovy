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

String test = "{\"paymentTransactionReq\":{\"msgHdr\":{\"msgId\":\"847586859632\",\"cnvId\":\"\",\"extRefId\":\"\",\"bizObjId\":\"\",\"appId\":\"\",\"timestamp\":\""+timestamp+"\"},\"msgBdy\":{\"paymentReq\":{\"custTxnRef\":\""+custTxnRef+"\",\"beneAccNo\":\""+beneAccNo+"\",\"beneName\":\""+beneName+"\",\"beneAddr1\":\""+beneAddr1+"\",\"beneAddr2\":\"\",\"ifsc\":\""+ifsc+"\",\"valueDate\":\""+valueDate+"\",\"tranCcy\":\"000\",\"tranAmount\":\""+tranAmount+"\",\"purposeCode\":\"Loan Disbursement\",\"remitInfo1\":\"\",\"remitInfo2\":\"\",\"clientCode\":\"SAMUAT\",\"paymentType\":\""+paymentType+"\",\"beneAccType\":\""+beneAccType+"\",\"remarks\":\"\",\"beneMail\":\"\",\"beneMobile\":\""+beneMobile+"\"}}}}";
logger.info("Status bank transfer processing:{}",test);
String urlParameters  = "requestJSON="+test+"&requesterCode=REQTR001&userName=Suresh Kumar R&userId=usr087&straightthroughprocessFlag=N";
byte[] postData = urlParameters.getBytes( StandardCharsets.UTF_8 );
request = "http://uatsambtrn.storyboarderp.com/EMSV3API/api/SamBTranAPIRequester/IDFCFirstDoDebit";
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
	return;
}
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
logger.info("{}:{}; status bank transfer completed:{}",response);
return; 


