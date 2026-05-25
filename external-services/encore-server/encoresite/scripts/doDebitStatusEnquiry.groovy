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
import com.sensei.encore.basedomain.model.bank.BankTransfer.BankTransferStatus;

logger.info("{}:{}; status bank transfer status enquiry started:{}");

String custRef = bankTransfer.getCustRef();
String bankRef = bankTransfer.getCoreRef();
String cmsRef = bankTransfer.getCmsRef();
String timestamp = bankTransfer.getTimeStampStr();

String test = "{\"PaymentsDebitTransactionInqReq\":{\"msgHdr\":{\"msgId\":\"847586859632\",\"cnvId\":\"\",\"extRefId\":\"\",\"bizObjId\":\"\",\"appId\":\"\",\"timestamp\":\""+timestamp+"\"},\"msgBdy\":{\"PymntsDbtTrnsctnInqReq\":{\"custRef\":\"" + custRef + "\",\"bankRef\":\"" + bankRef + "\",\"cmsRef\":\"" + cmsRef + "\",\"instNo\":\"\",\"clientCode\":\"SAMUAT\"}}}}";
String urlParameters  = "requestJSON="+test+"&requesterCode=REQTR001&userName=Suresh Kumar R&userId=usr087&straightthroughprocessFlag=Y";
logger.info("{}:{}; status bank transfer status enquiry processing:{}",test);
byte[] postData = urlParameters.getBytes( StandardCharsets.UTF_8 );
request = "http://uatsambtrn.storyboarderp.com/EMSV3API/api/SamBTranAPIRequester/IDFCFirstDoDebitStatusEnquiry";
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
	JSONObject status =(JSONObject)PymntsDbtTxnInqResp.get("status");
	JSONObject statusDesc =(JSONObject)PymntsDbtTxnInqResp.get("statusDesc");
	JSONObject errorCode =(JSONObject)PymntsDbtTxnInqResp.get("errorCode");
	if(status.equalsIgnoreCase("S") && errorCode.equalsIgnoreCase("ENQ000")){
		status = BankTransferStatus.SUCCESS;
		reason=statusDesc.toString();	
	}else if(status.equalsIgnoreCase("F") && errorCode.equalsIgnoreCase("ENQ000")){
		status = BankTransferStatus.FAILURE;
		reason=statusDesc.toString();
	}else {
		status = BankTransferStatus.REQUESTED;
		reason=statusDesc.toString();
	}
	
}
bankTransfer.setStatus(status);
bankTransfer.setReason(reason);
logger.info("{}:{}; status bank transfer status enquiry completed:{}",response);
return; 


