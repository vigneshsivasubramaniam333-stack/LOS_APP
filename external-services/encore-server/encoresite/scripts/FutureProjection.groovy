
headerStr = ",Customer Phone No.,Guarantor CustomerId,Guarantor Name,Guarantor phone No.,RO Name,Branch Name,Vertical";
resultStr = "";
if (customerRepository == null || bankRepository == null || customerId == null || loanOdProfile == null || branchCode == null || branch == null || customer == null)
    return;
resultStr = ","+customer.getContact().getPhone1()+",";
if (guarantor != null)
    resultStr = resultStr+guarantor.getCustomerId()+","+guarantor.getCustomerName()+","+guarantor.getContact().getPhone1()+",";
else
    resultStr = resultStr+",,,";
if (customerAssociate != null)
    resultStr = resultStr+customerAssociate.getName()+",";
else
    resultStr = resultStr+",";
resultStr = resultStr+branch.getBranchName()+",";
if(customer.getDemography() != null)
    resultStr = resultStr+customer.getDemography().getSector();