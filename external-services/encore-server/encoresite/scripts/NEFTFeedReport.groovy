import com.sensei.encore.loandomain.model.*

for (Object[] data : items) {
	int col = 0;
	for (Object o : data) {
		String s = "";
		if (o != null)
			s = o.toString();
		col++;
		if (col == 8) {
			List<LoanOdDisbursement> disbursements = loanOdRepository.findDisbursements(s);
			for(LoanOdDisbursement disbursement : disbursements){
				disbursement.setFeedFileName(fileName);
				loanOdRepository.storeDisbursement(disbursement);
			}
		}
	}
}