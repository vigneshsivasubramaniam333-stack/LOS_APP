import com.sensei.encore.application.dto.LoanOdAccountDto
import com.sensei.encore.application.dto.LoanOdAccountWSDto
import com.sensei.encore.application.dto.LoanOdSummaryDto
import com.sensei.encore.application.dto.LoanOdSummaryWSDto
import com.sensei.encore.application.facade.LoanWebServiceFacade
import com.sensei.encore.application.facade.LoanOdServiceFacade
import com.sensei.encore.basedomain.model.calendar.Tenure
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource
import com.sensei.encore.application.assembler.LoanOdSummaryAssembler

LoanWebServiceFacade loanWebService = applicationContext.getBean(LoanWebServiceFacade.class)

LoanOdAccountWSDto loanOdAccountDto  =  new LoanOdAccountWSDto();
LoanOdSummaryWSDto summaryWSDto = new LoanOdSummaryWSDto();
AccountId=params.get("accountId_text")
if(AccountId==null || AccountId == "") {
    loanOdAccountDto.setProductCode(params.get("productCode"))
    loanOdAccountDto.setBranchCode(params.get("branchCode"))
    loanOdAccountDto.setTenureMagnitude(params.get("tenureMagnitude"))
    loanOdAccountDto.setTenureUnit(params.get("tenureUnit"))
    loanOdAccountDto.setAmountMagnitude(params.get("amountMagnitude"))
    loanOdAccountDto.setOpenedOnDate(params.get("openedOnDate"))
    summaryWSDto = loanWebService.findPreOpenSummary(loanOdAccountDto);

}
else
    summaryWSDto = loanWebService.findSummary(AccountId,false,null)

params.put("summaryWSDto", summaryWSDto)
params.put("Fees", new JRBeanCollectionDataSource(summaryWSDto.getFees()))
params.put("repaymentSchedule", new JRBeanCollectionDataSource(summaryWSDto.getRepaymentSchedule()))
params.put("NetDisbursementAmount", summaryWSDto.getAmount().toDouble())
params.put("APR", summaryWSDto.getApr())
params.put("NoOfEMIs", summaryWSDto.repaymentSchedule.size().toString())
params.put("InterestPayable", summaryWSDto.getRepaymentScheduleNormalInterest().toDouble())
params.put("AmountPayable", summaryWSDto.getAmount().toDouble() + summaryWSDto.getRepaymentScheduleNormalInterest().toDouble())
















