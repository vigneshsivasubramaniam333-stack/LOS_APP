import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

status = "Not Sent";

if (phone == null || phone.isEmpty()) {
  logger.info("PhoneEmpty {} {}", message, status);
  return;
}
String smsUrl = "http://alerts.solutionsinfini.com/api/v3/index.php?method=sms&api_key=Ac50f3539fcea64ebba9fec7b9ed8386e&sender=SAMFIN&format=json&custom=1,2&flash=0";
smsUrl = smsUrl + "&to=" + phone;
smsUrl = smsUrl + "&message=" + URLEncoder.encode(message);


def response = new URL(smsUrl).getText();
def jsonSlurper = new JsonSlurper();
def object = jsonSlurper.parseText(response);
//println response;
//println object.data["0"].status;
status = object.data["0"].status;
logger.info("{} {} {}", phone, message, status);

if ("AWAITED-DLR".equals(status))
   status = "Ok " + status;

