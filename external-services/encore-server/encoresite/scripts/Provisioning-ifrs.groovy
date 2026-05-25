
import com.sensei.encore.loandomain.model.LoanOdProvisioning.ProvisioningType

assetCategory = oldAssetCategory;
if (provisioningType == ProvisioningType.CUSTOMER_EXPIRY) {
    provisionedAmount = balance;
    assetCategory = "DOUBTFUL";
    return;
}
if (daysPastDue == 0) {
    provisionedAmount = ((balance * new BigDecimal("0.4"))/100).setScale(2, BigDecimal.ROUND_HALF_UP);
    assetCategory = "STANDARD";
} else if (daysPastDue >= 1 && daysPastDue <= 89) {
    provisionedAmount = (((balance.add(interestDue)) * new BigDecimal("0.4"))/100).setScale(2, BigDecimal.ROUND_HALF_UP);
    if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD"))
       assetCategory = "STANDARD";
} else if (daysPastDue >= 90 && daysPastDue <= 450) {
    provisionedAmount = balance;
    if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD"))
       assetCategory = "SUBSTANDARD";
} else if (daysPastDue >= 451 && daysPastDue <= 816) {
    provisionedAmount = balance;
    if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD") || oldAssetCategory.equals("SUBSTANDARD"))
       assetCategory = "DOUBTFUL-1";
} else if (daysPastDue >= 817 && daysPastDue <= 1547) {
    provisionedAmount = balance;
    if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD") || oldAssetCategory.equals("SUBSTANDARD") || oldAssetCategory.equals("DOUBTFUL-1"))
       assetCategory = "DOUBTFUL-2";
} else if (daysPastDue > 1547) {
    provisionedAmount = balance;
    if (oldAssetCategory == null || oldAssetCategory.isEmpty() || oldAssetCategory.equals("STANDARD") || oldAssetCategory.equals("SUBSTANDARD") || oldAssetCategory.equals("DOUBTFUL-1") || oldAssetCategory.equals("DOUBTFUL-2"))
       assetCategory = "DOUBTFUL-3";
}