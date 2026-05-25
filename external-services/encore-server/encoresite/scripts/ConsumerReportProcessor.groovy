import java.time.format.*;
import java.time.*;
import java.lang.*;
import com.sensei.encore.basedomain.model.money.Money;
import com.sensei.encore.basedomain.model.money.PercentRate;

        String pnSegment = "PN03N01";
		String customerName = (StringValue[0] !=null?StringValue[0].toString(): "")+(StringValue[1] !=null?" "+StringValue[1].toString(): "")+(StringValue[2] !=null?" "+StringValue[2].toString(): "");
		customerName.replace("&", " ");
		customerName.replace("-", " ");
		customerName.replace("/", " ");
		String name1 = "",name2="",name3="";
		if(customerName.length()<=26)
			name1 = customerName;
		else if (customerName.length()>26 && customerName.length()<=52) {
			name1 = customerName.substring(0, 26);
			name2 = customerName.substring(26, customerName.length()-1);
		} else if(customerName.length()>52) {
			name1 = customerName.substring(0, 26);
		    name2 = customerName.substring(26, 52);
			name3 = customerName.substring(52, customerName.length()-1);
		}
		String firstName = "01"+ (name1.length()<10?"0"+name1.length():name1.length())+name1;
		String middleName = name2.isEmpty()?"":"02"+(name2.length()<10?"0"+name2.length():name2.length())+name2;
		String lastName = name3.isEmpty()?"":"03"+(name3.length()<10?"0"+name3.length():name3.length())+name3;
		String dobSegment = "0708"+StringValue[3].toString();
		String genderSegment = "0801"+StringValue[4].toString();
		String idSegment = "";
		String id1 = (StringValue[5] !=null && !StringValue[5].toString().equals("")?"I0101020102"+StringValue[5].toString().length()+StringValue[5].toString():"");
		String id2 = (id1.equals("") && StringValue[9] !=null && !StringValue[9].toString().equals(""))?"I0101020302"+StringValue[9].toString().length()+StringValue[9].toString() :
			(!id1.equals("") && StringValue[9] !=null && !StringValue[9].toString().equals(""))?"I0201020302"+StringValue[9].toString().length()+StringValue[9].toString():"";
		String id3 = (id1.equals("") && id2.equals("") && StringValue[10] !=null && !StringValue[10].toString().equals(""))?"I0101020402"+StringValue[10].toString().length()+StringValue[10].toString() : 
			(id1.equals("") && !id2.equals("") && StringValue[10] !=null && !StringValue[10].toString().equals("")) ? "I0201020402"+StringValue[10].toString().length()+StringValue[10].toString():
			(!id1.equals("") && id2.equals("") && StringValue[10] !=null && !StringValue[10].toString().equals("")) ? "I0201020402"+StringValue[10].toString().length()+StringValue[10].toString():
			(!id1.equals("") && !id2.equals("") && StringValue[10] !=null && !StringValue[10].toString().equals("")) ? "I0301020402"+StringValue[10].toString().length()+StringValue[10].toString():"";
		String id4 = (id1.equals("") && id2.equals("") && id3.equals("") && StringValue[13] !=null && !StringValue[13].toString().equals(""))?"I0101020502"+StringValue[13].toString().length()+StringValue[13].toString():
			(id1.equals("") && id2.equals("") && !id3.equals("") && StringValue[13] !=null && !StringValue[13].toString().equals("")) ? "I0201020502"+StringValue[13].toString().length()+StringValue[13].toString():
			(id1.equals("") && !id2.equals("") && id3.equals("") && StringValue[13] !=null && !StringValue[13].toString().equals("")) ? "I0201020502"+StringValue[13].toString().length()+StringValue[13].toString():
			(!id1.equals("") && id2.equals("") && id3.equals("") && StringValue[13] !=null && !StringValue[13].toString().equals("")) ? "I0201020502"+StringValue[13].toString().length()+StringValue[13].toString():
			(!id1.equals("") && !id2.equals("") && id3.equals("") && StringValue[13] !=null && !StringValue[13].toString().equals("")) ? "I0301020502"+StringValue[13].toString().length()+StringValue[13].toString():
			(!id1.equals("") && id2.equals("") && !id3.equals("") && StringValue[13] !=null && !StringValue[13].toString().equals("")) ? "I0301020502"+StringValue[13].toString().length()+StringValue[13].toString():
			(id1.equals("") && !id2.equals("") && !id3.equals("") && StringValue[13] !=null && !StringValue[13].toString().equals("")) ? "I0301020502"+StringValue[13].toString().length()+StringValue[13].toString():
			(!id1.equals("") && !id2.equals("") && !id3.equals("") && StringValue[13] !=null && !StringValue[13].toString().equals("")) ? "I0401020502"+StringValue[13].toString().length()+StringValue[13].toString():"";
		String id5 = (id1.equals("") && id2.equals("") && id3.equals("") && id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0101020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(id1.equals("") && id2.equals("") && id3.equals("") && !id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0201020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(id1.equals("") && id2.equals("") && !id3.equals("") && id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0201020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(id1.equals("") && !id2.equals("") && id3.equals("") && id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0201020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(!id1.equals("") && id2.equals("") && id3.equals("") && id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0201020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(!id1.equals("") && !id2.equals("") && id3.equals("") && id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0301020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(!id1.equals("") && id2.equals("") && !id3.equals("") && id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0301020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(!id1.equals("") && id2.equals("") && id3.equals("") && !id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0301020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(id1.equals("") && !id2.equals("") && !id3.equals("") && id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0301020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(id1.equals("") && !id2.equals("") && id3.equals("") && !id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0301020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(id1.equals("") && id2.equals("") && !id3.equals("") && !id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0301020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(!id1.equals("") && !id2.equals("") && !id3.equals("") && id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0401020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(!id1.equals("") && !id2.equals("") && id3.equals("") && !id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0401020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(id1.equals("") && !id2.equals("") && !id3.equals("") && !id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0401020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(!id1.equals("") && id2.equals("") && !id3.equals("") && !id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0401020602"+StringValue[14].toString().length()+StringValue[14].toString():
			(!id1.equals("") && !id2.equals("") && !id3.equals("") && !id4.equals("") && StringValue[14] !=null && !StringValue[14].toString().equals(""))?"I0501020602"+StringValue[14].toString().length()+StringValue[14].toString():"";
		if (!id1.equals(""))
			idSegment = "ID03" +id1;
		if (!id2.equals(""))
			idSegment = idSegment+ "ID03" +id2;
		if (!id3.equals(""))
			idSegment = idSegment+ "ID03" +id3;
		if (!id4.equals(""))
			idSegment = idSegment+ "ID03" +id4;
		if (!id5.equals(""))
			idSegment = idSegment+ "ID03" +id5;
		String telephoneSegment = "", telephoneType = "";
		String phone1 = (StringValue[17] !=null && !StringValue[17].toString().equals(""))?StringValue[17].toString().length()+StringValue[17].toString():"";
		String phone2 = (StringValue[18] !=null && !StringValue[18].toString().equals(""))?StringValue[18].toString().length()+StringValue[18].toString():"";
		if (!phone1.equals("")) {
			telephoneSegment = "PT03T01"+ phone1;
			telephoneType = "030200";
		}
		if (!phone1.equals("") && !phone2.equals("")) {
			telephoneSegment = telephoneSegment + "T02"+ phone2;
			telephoneType = "030200";
		}
		if (phone1.equals("") && !phone2.equals("")) {
			telephoneSegment = "PT03T01"+ phone2;
			telephoneType = "030200";
		}
		String email1 = ((StringValue[23] !=null && !StringValue[23].toString().equals(""))?"EC03C0101"+StringValue[23].toString().length()+StringValue[23].toString(): "");
		String addressSegment = "PA03";
		String address = (StringValue[25] !=null?StringValue[25].toString(): "");
		String add1 = "",add2="",add3="",add4="",add5="";
		if(address.length()<=40)
			add1 = address;
		else if (address.length()>40 && address.length()<=80) {
			add1 = address.substring(0, 40);
			add2 = address.substring(40, address.length()-1);
		} else if(address.length()>80 && address.length()<=120) {
			add1 = address.substring(0, 40);
		    add2 = address.substring(40, 80);
			add3 = address.substring(80, address.length()-1);
		} else if(address.length()>120 && address.length()<=160) {
			add1 = address.substring(0, 40);
		    add2 = address.substring(40, 80);
			add3 = address.substring(80, 120);
			add4 = address.substring(120, address.length()-1);
		} else if(address.length()>160) {
			add1 = address.substring(0, 40);
		    add2 = address.substring(40, 80);
			add3 = address.substring(80, 120);
			add4 = address.substring(120, 160);
			add5 = address.substring(160, address.length()-1);
		}
		String address1 = "A0101"+ (add1.length()<10?"0"+add1.length():add1.length())+add1;
		String address2 = add2.isEmpty()?"":"02"+(add2.length()<10?"0"+add2.length():add2.length())+add2;
		String address3 = add3.isEmpty()?"":"03"+(add3.length()<10?"0"+add3.length():add3.length())+add3;
		String address4 = add4.isEmpty()?"":"04"+(add4.length()<10?"0"+add4.length():add4.length())+add4;
		String address5 = add5.isEmpty()?"":"05"+(add5.length()<10?"0"+add5.length():add5.length())+add5;
		String stateCode = "0602"+StringValue[26].toString();
		String pinCode = "070"+StringValue[27].toString().length()+StringValue[27].toString();
		String addressType = "0802"+StringValue[28].toString();
		String segmentTag = "TL04T0010110NB779200010211SAMUNNATIFI";
		String accountNumber = "03"+StringValue[37].toString().length()+StringValue[37].toString();
		String accountType = (StringValue[38]!=null?"0402"+StringValue[38].toString():"");
		String ownerShipIndicator = "0501"+StringValue[39].toString();
		String disbursementDate = StringValue[40]!=null?"0808"+StringValue[40].toString(): "";
		String lastRepaymentDate = StringValue[41]!=null?"0908"+StringValue[41].toString(): "";
		String closedDate = StringValue[42]!=null?"1008"+StringValue[42].toString(): "";
		String currentDate = StringValue[43]!=null?"1108"+StringValue[43].toString(): "";
		String sanctionedAmount = StringValue[44]!=null?"120"+StringValue[44].toString().length()+StringValue[44].toString(): "";
		String currentBalance = (StringValue[45]!=null && StringValue[45].toString().length() != 10)?"130"+StringValue[45].toString().length()+StringValue[45].toString():
								(StringValue[45]!=null && StringValue[45].toString().length() == 10)?"1310"+StringValue[45].toString():"";
		String amountOverDue = StringValue[46]!=null ? "140"+StringValue[46].toString().length()+StringValue[46].toString(): "";
		String daysPastDue = (!StringValue[47].toString().equals("0") && (Integer.parseInt(StringValue[47].toString())<= 998))?"150"+StringValue[47].toString().length()+StringValue[47].toString():
		                     (!StringValue[47].toString().equals("0") && Integer.parseInt(StringValue[47].toString())> 998)?"15003999": "";
		String assetCategory = "2602"+StringValue[55].toString();
		String rateOfInterest = "380"+StringValue[60].toString().length()+StringValue[60].toString();
		String tenure = "390"+StringValue[61].toString().length()+StringValue[61].toString();
		String emiAmount = !StringValue[62].toString().equals("null")?"400"+StringValue[62].toString().length()+StringValue[62].toString():"";
		String frequency = "4402"+StringValue[66].toString();
		String netOrGrossIncomeIndicator = "4801N";
		String monthlyOrAnnualIncomeIndicator = "4901A";
		String endOfData = "ES02**";
		
StringBuilder builder = new StringBuilder();
builder.append(pnSegment).append(firstName).append(middleName).append(lastName).append(dobSegment).append(genderSegment).append(idSegment);
builder.append(telephoneSegment).append(telephoneType).append(email1).append(addressSegment).append(address1).append(address2).append(address3).append(address4).append(address5);
builder.append(stateCode).append(pinCode).append(addressType).append(segmentTag).append(accountNumber).append(accountType).append(ownerShipIndicator).append(disbursementDate);
builder.append(lastRepaymentDate).append(closedDate).append(currentDate).append(sanctionedAmount).append(currentBalance).append(amountOverDue).append(daysPastDue).append(assetCategory);
builder.append(rateOfInterest).append(tenure).append(emiAmount).append(frequency).append(netOrGrossIncomeIndicator).append(monthlyOrAnnualIncomeIndicator);
builder.append(endOfData);
		results[0] = builder.toString();