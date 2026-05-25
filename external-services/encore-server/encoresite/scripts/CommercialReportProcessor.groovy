import java.time.format.*;
import java.time.*;
import java.lang.*;
import com.sensei.encore.basedomain.model.location.State;
import org.apache.commons.lang3.StringUtils;

		String newLine = System.getProperty("line.separator");
        String branchCode = StringValue[0] ==null?"":(String)StringValue[0];
		String customerName = (StringValue[1]==null?"":(String)StringValue[1]) + (StringValue[42]==null?"":(" "+(String)StringValue[42])) + ((StringValue[43]==null?"":(" "+(String)StringValue[43])));
		customerName.replace("&", " ");
		customerName.replace("-", " ");
		customerName.replace("/", " ");
		String customerType = (String)StringValue[2];
		String businessCategory = StringValue[3] ==null?"":(String)StringValue[3];
		String businessType = StringValue[4] ==null?"":(String)StringValue[4];
		String locationType = StringValue[5] ==null?"":(String)StringValue[5];
		String address1 = StringValue[6] ==null?"":(String)StringValue[6];
		address1.replaceAll("\\|", " ");
    	address1.replaceAll("/", " ");
    	address1.replaceAll("\"", " ");
    	address1.replaceAll("\\?", " ");
		String relatedType = StringValue[7] ==null?"":(String)StringValue[7];
		String relationShip = StringValue[8] ==null?"":(String)StringValue[8];
		String accountId = StringValue[9] ==null?"":(String)StringValue[9];
		int totalDisbursedAmount = ((BigDecimal)StringValue[10]).intValue();
		String currencyCode = StringValue[11] ==null?"":(String)StringValue[11];
		String frequency = StringValue[12] ==null?"":(String)StringValue[12];
		int balance = -((BigDecimal)StringValue[13]).intValue();
		String assetCategory = StringValue[14] ==null?"":(String)StringValue[14];
		int totalDemandDue = ((BigDecimal)StringValue[15]).intValue();
		int operationalStatus = (int)StringValue[16];
		String guarantorLegalConstitution = StringValue[17] ==null?"":(String)StringValue[17];
		String guarantorSalutation = StringValue[18] ==null?"":(String)StringValue[18];
		String guarantorName = (StringValue[19]==null?"":(String)StringValue[19]) + (StringValue[44]==null?"":(" "+(String)StringValue[44])) + ((StringValue[45]==null?"":(" "+(String)StringValue[45])));
		String guarantorGender = StringValue[20] ==null?"":(String)StringValue[20];
		String gDateOfBirth = (StringValue[21] == null) ? "":StringValue[21].toString();
		String gPAN = StringValue[22] ==null?"":(String)StringValue[22];
		String gUid = StringValue[23] ==null?"":(String)StringValue[23];
		String gAddress1 = StringValue[24] ==null?"":(String)StringValue[24];
		gAddress1.replaceAll("\\|", " ");
    	gAddress1.replaceAll("/", " ");
    	gAddress1.replaceAll("\"", " ");
    	gAddress1.replaceAll("\\?", " ");
		String gAddress2 = StringValue[25] ==null?"":(String)StringValue[25];
		gAddress2.replaceAll("\\|", " ");
    	gAddress2.replaceAll("/", " ");
    	gAddress2.replaceAll("\"", " ");
    	gAddress2.replaceAll("\\?", " ");
		String gAddress3 = StringValue[26] ==null?"":(String)StringValue[26];
		gAddress3.replaceAll("\\|", " ");
    	gAddress3.replaceAll("/", " ");
    	gAddress3.replaceAll("\"", " ");
    	gAddress3.replaceAll("\\?", " ");
		String gCity = StringValue[27] ==null?"":(String)StringValue[27];
		String gDistrict = StringValue[28] ==null?"":(String)StringValue[28];
		String gState = StringValue[29] ==null?"":(String)StringValue[29];
		Integer gPinCode = StringValue[30] ==null?0:(Integer)StringValue[30];
		String gCountry = StringValue[31] ==null?"":(String)StringValue[31];
		String guarantorBusinessCategory = StringValue[32] ==null?"":(String)StringValue[32];
		String guarantorbusinessType = StringValue[33] ==null?"":(String)StringValue[33];
		String dateOfIncorporation = (StringValue[34] == null || StringValue[34].equals("")) ? "":StringValue[34].toString();
		String stateCode = StringValue[36] ==null?"":(String)StringValue[36];
		Integer pinCode = StringValue[37] ==null?0:(Integer)StringValue[37];
		String lastProvisioningDate = (StringValue[38] == null) ? "":StringValue[38].toString();
		String managerialPerson = StringValue[39] == null?"":(String)StringValue[39];
		String countryCode = StringValue[40] == null?"":(String)StringValue[40];
		String guarantorCustomerId = StringValue[41] == null?"":(String)StringValue[41];
		int princiaplRepaid = ((BigDecimal)StringValue[46]).intValue();
		String pan =  StringValue[47] == null?"":(String)StringValue[47];
		String address2 = StringValue[48] ==null?"":(String)StringValue[48];
		address2.replaceAll("\\|", " ");
		address2.replaceAll("/", " ");
		address2.replaceAll("\"", " ");
		address2.replaceAll("\\?", " ");
		String address3 = StringValue[49] ==null?"":(String)StringValue[49];
		address3.replaceAll("\\|", " ");
		address3.replaceAll("/", " ");
		address3.replaceAll("\"", " ");
		address3.replaceAll("\\?", " ");
		String cityCode = StringValue[50] ==null?"":(String)StringValue[50];
		String districtCode = StringValue[51] ==null?"":(String)StringValue[51];
		String phone1 = StringValue[52] ==null?"":(String)StringValue[52];
		String phone2 = StringValue[53] ==null?"":(String)StringValue[53];
		String gPhone1 = StringValue[54] ==null?"":(String)StringValue[54];
		String gPhone2 = StringValue[55] ==null?"":(String)StringValue[55];
		String closedOnValueDate = (StringValue[56] == null) ? "":StringValue[56].toString();
		Long dpd = StringValue[57] ==null?0:(Long)StringValue[57];
		String gcin = StringValue[58] ==null?"":(String)StringValue[58];
		String gDocumentNo1 = StringValue[59] ==null?"":(String)StringValue[59];
		String gDocumentNo2 = StringValue[60] ==null?"":(String)StringValue[60];
		String gDocumentNo3 = StringValue[61] ==null?"":(String)StringValue[61];
		String sanctionDate = (StringValue[62] == null) ? "":StringValue[62].toString();
		String creditType = StringValue[63] ==null?"":(String)StringValue[63];
		String salutation = StringValue[64] ==null?"":(String)StringValue[64];
		String gender = StringValue[65] ==null?"":(String)StringValue[65];
		String dateOfBirth = (StringValue[66] == null) ? "":StringValue[66].toString();
		String document1 = StringValue[67] ==null?"":(String)StringValue[67];
		String document2 = StringValue[68] ==null?"":(String)StringValue[68];
		String document3 = StringValue[69] ==null?"":(String)StringValue[69];
		String uid = StringValue[70] ==null?"":(String)StringValue[70];
		if(!guarantorGender.isEmpty() && guarantorGender.startsWith("M"))
			guarantorGender = "1";
		else if(!guarantorGender.isEmpty() && guarantorGender.startsWith("F"))
			guarantorGender = "2";
		if(!gender.isEmpty() && gender.startsWith("M"))
			gender = "1";
		else if(!gender.isEmpty() && gender.startsWith("F"))
			gender = "2";
		String operationalStatusStr = null;
		if (operationalStatus == 0 || operationalStatus == 3) {
			operationalStatusStr = "01";
		} else if (operationalStatus == 2)
			operationalStatusStr = "02";
		else if (operationalStatus == 4) {
			operationalStatusStr = "05";
		} else if (operationalStatus == 5)
			operationalStatusStr = "03";
		if (assetCategory.toUpperCase().startsWith("STANDARD"))
			assetCategory = "0001";
		else if (assetCategory.toUpperCase().startsWith("SUBSTANDARD"))
			assetCategory = "0002";
		else if (assetCategory.toUpperCase().startsWith("DOUBTFUL"))
			assetCategory = "0003";
		else if (assetCategory.toUpperCase().startsWith("LOSS"))
			assetCategory = "0004";
		String branchName = bankRepository.findBranch(branchCode).getBranchName();
    	if (branchCode.equalsIgnoreCase("HO"))
    		branchName = "Chennai";
    	if (branchName.length() >15)
    		branchName = branchName.substring(0, 14);
		String altStateCode = "";
		State state1 = locationRepository.findState(countryCode, stateCode);
    	if (state1 != null)
    		altStateCode = state1.getAlt1StateCode();
		String altGuarantorStateCode = "";
		State state2 = locationRepository.findState(gCountry, gState);
    	if (state2 != null)
    		altGuarantorStateCode = state2.getAlt1StateCode();
		String offcDUNSNo = "999999999";
		String countryAltCode = "079";
		int drwingPower = totalDisbursedAmount-princiaplRepaid;
		String gEntityName = null;
		String gName = null;
		if (guarantorLegalConstitution.equals("1"))
			gEntityName = guarantorName;
		else
			gName = guarantorName;
		
		StringBuilder builder = new StringBuilder();
		builder.append("BS|").append(branchName).append("||").append(customerName).append("||||").append(pan).append("|||||").append(customerType).append("|").append(businessCategory).append("|").append(businessType)
			.append("||||||||||||||").append(newLine);
		builder.append("AS|").append(locationType==null?"":locationType).append("|").append(offcDUNSNo).append("|").append(address1).append("|").append(address2).append("|").append(address3).append("|").append(cityCode).append("|").append(districtCode)
			.append("|").append(altStateCode).append("|").append(pinCode).append("|").append(countryAltCode).append("|").append(phone1).append("||").append(phone2).append("|||||").append(newLine);
		builder.append("RS|").append("999999999|2|56||01|09|").append(salutation).append("|").append(customerName).append("|").append(gender).append("||").append(dateOfIncorporation).append("|").append(dateOfBirth).append("|").append(pan).append("|")
			.append(document1).append("|").append(document2).append("|").append(document3).append("|").append(uid).append("||||||||").append(address1).append("|").append(address2).append("|").append(address3).append("|")
			.append(cityCode).append("|").append(districtCode).append("|").append(altStateCode).append("|").append(pinCode).append("|").append(countryAltCode).append("|").append(phone1).append("||").append(phone2).append("|||||").append(newLine);
		builder.append("CR|").append(accountId).append("||").append(sanctionDate).append("|").append(totalDisbursedAmount).append("|").append(currencyCode).append("|").append(creditType).append("||").append(frequency)
			.append("|").append(drwingPower).append("|").append(balance).append("||||").append(assetCategory).append("|").append(lastProvisioningDate).append("|");
		if (dpd < 4)
			totalDemandDue = 0;
		if (dpd==0) {
			builder.append(totalDemandDue).append("|||||||||");
		} else if (dpd >=1 && dpd <= 30) {
			builder.append(totalDemandDue).append("|").append(totalDemandDue).append("||||||||");
		} else if (dpd >=31 && dpd <= 60) {
			builder.append(totalDemandDue).append("||").append(totalDemandDue).append("|||||||");
		} else if (dpd >=61 && dpd <= 90) {
			builder.append(totalDemandDue).append("|||").append(totalDemandDue).append("||||||");
		} else if (dpd >=91 && dpd <= 180) {
			builder.append(totalDemandDue).append("||||").append(totalDemandDue).append("|||||");
		} else if (dpd > 180) {
			builder.append(totalDemandDue).append("|||||").append(totalDemandDue).append("||||");
		}
		builder.append(operationalStatusStr).append("|").append(closedOnValueDate).append("||||||||0|||||||||||").append(newLine);
		if (!StringUtils.isEmpty(guarantorCustomerId) && (!StringUtils.isEmpty(gPAN) || !StringUtils.isEmpty(gUid) || !StringUtils.isEmpty(gcin) || 
				!StringUtils.isEmpty(gDocumentNo1) || !StringUtils.isEmpty(gDocumentNo2) || !StringUtils.isEmpty(gDocumentNo3))) {
			builder.append("GS|").append(offcDUNSNo).append("|").append(guarantorLegalConstitution).append("|").append(guarantorBusinessCategory).append("|").append(guarantorbusinessType).append("|");
			if (gEntityName != null) {
				builder.append(gEntityName).append("|||||||").append(gPAN).append("|||||||||||");
			} else if(gName != null) {
				builder.append("|").append(gName).append("||").append(guarantorGender).append("|||").append(gDateOfBirth).append("|").append(gPAN).append("||||").append(gUid).append("|||||||");
			}
			builder.append(gAddress1).append("|").append(gAddress2).append("|").append(gAddress3).append("|").append(gCity).append("|").append(gDistrict).append("|")
				.append(altGuarantorStateCode).append("|").append(gPinCode==0?"":gPinCode).append("|").append(countryAltCode).append("|").append(gPhone1).append("||").append(gPhone2).append("|||||").append(newLine);
		}
results[0] = builder.toString();