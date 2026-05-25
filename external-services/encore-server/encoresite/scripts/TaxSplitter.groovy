BigDecimal tax1 = BigDecimal.ZERO, tax2 = BigDecimal.ZERO, tax3 = BigDecimal.ZERO, tax4 = BigDecimal.ZERO;
String bankStateCode = "";
String branchStateCode = "";
bank = bankRepository.findBank();
branch = bankRepository.findBranch(branchCode);
hqBranch = bankRepository.findHQBranch();
if (bank.getContact() != null)
	bankStateCode = bank.getContact().getStateCode();
if (hqBranch != null && hqBranch.getContact() != null)
	bankStateCode = hqBranch.getContact().getStateCode();
if (branch != null && branch.getContact() != null)
	branchStateCode = branch.getContact().getStateCode();
if (customerStateCode == null || customerStateCode.isEmpty()) {
	customer = customerRepository.findCustomer(customerId);
	customerStateCode = (customer == null || customer.getContact() == null)? "" : customer.getContact().getStateCode();
}
if (productCode.equalsIgnoreCase("TESTLOCALPRODUCT")) {
    // Localized processing
	if (customerStateCode.equals(branchStateCode)) {
		tax2 = tax/2;
		tax3 = tax/2;
	} else {
		tax1 = tax;
	}
	taxBranchCode = branchCode;
} else {
    // Centralized processing
	if (customerStateCode.equals(bankStateCode)) {
		tax2 = tax/2;
		tax3 = tax/2;
	} else {
		tax1 = tax;
	}
	taxBranchCode = branchCode;
	// if (hqBranch != null)
	//    taxBranchCode = hqBranch.getBranchCode();
}
taxAmount = new BigDecimal[4];
taxAmount[0] = tax1;
taxAmount[1] = tax2;
taxAmount[2] = tax3;
taxAmount[3] = tax4;
