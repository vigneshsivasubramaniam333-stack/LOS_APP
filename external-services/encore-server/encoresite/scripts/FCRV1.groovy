
branchSetBranch = bankRepository.findBranchSetBranch("BRS1", branchCode);
if (branchSetBranch != null)
	feeCharge = "200".toBigDecimal();
else
	feeCharge = (amount * new BigDecimal ("0.01")).setScale(2, BigDecimal.ROUND_HALF_UP);