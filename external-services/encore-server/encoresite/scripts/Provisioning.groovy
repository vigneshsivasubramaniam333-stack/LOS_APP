assetCategory = oldAssetCategory;
if (daysPastDue == 0) {
	provisionedAmount = ((balance * new BigDecimal("0.5"))/100).setScale(2, BigDecimal.ROUND_HALF_UP);
	assetCategory = "STANDARD";
} else if (daysPastDue >= 1 && daysPastDue <= 30) {
	provisionedAmount = ((balance * new BigDecimal("0.5"))/100).setScale(2, BigDecimal.ROUND_HALF_UP);
	if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD"))
		assetCategory = "SUBSTANDARD";
} else if (daysPastDue >= 31 && daysPastDue <= 60) {
	provisionedAmount = ((balance * new BigDecimal("0.5"))/100).setScale(2, BigDecimal.ROUND_HALF_UP);
	if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD"))
		assetCategory = "SUBSTANDARD";
} else if (daysPastDue >= 61 && daysPastDue <= 90) {
	provisionedAmount = ((balance * new BigDecimal("0.5"))/100).setScale(2, BigDecimal.ROUND_HALF_UP);
	if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD") || oldAssetCategory.equals("SUBSTANDARD"))
		assetCategory = "DOUBTFUL";
} else if (daysPastDue >= 91 && daysPastDue <= 180) {
	provisionedAmount = ((balance * new BigDecimal("1"))/100).setScale(2, BigDecimal.ROUND_HALF_UP);
	if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD") || oldAssetCategory.equals("SUBSTANDARD"))
		assetCategory = "DOUBTFUL";
} else if (daysPastDue >= 181 && daysPastDue <= 360) {
	provisionedAmount = ((balance * new BigDecimal("30"))/100).setScale(2, BigDecimal.ROUND_HALF_UP);
	if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD") || oldAssetCategory.equals("SUBSTANDARD"))
		assetCategory = "DOUBTFUL";
} else if (daysPastDue >= 361 && daysPastDue <= 450) {
	provisionedAmount = ((balance * new BigDecimal("50"))/100).setScale(2, BigDecimal.ROUND_HALF_UP);
	if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD") || oldAssetCategory.equals("SUBSTANDARD"))
		assetCategory = "DOUBTFUL";
} else if (daysPastDue >= 451 && daysPastDue <= 510) {
	provisionedAmount = ((balance * new BigDecimal("75"))/100).setScale(2, BigDecimal.ROUND_HALF_UP);
	if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD") || oldAssetCategory.equals("SUBSTANDARD"))
		assetCategory = "DOUBTFUL";
} else if (daysPastDue >= 511 && daysPastDue <= 540) {
	provisionedAmount = balance;
	if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD") || oldAssetCategory.equals("SUBSTANDARD"))
		assetCategory = "DOUBTFUL";
} else if (daysPastDue > 540) {
	provisionedAmount = balance;
	if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD") || oldAssetCategory.equals("SUBSTANDARD") || oldAssetCategory.equals("DOUBTFUL"))
		assetCategory = "LOSS";
}