#!/bin/bash

# This utility dumps a subset of database that contains just the seed along with the data specific to a user-provided account id
# The output of this command is a self-contained-dump file that can be restored on a developer database
# This simplifies troubleshooting a production scenario as entire database dump need not be transported to developer system

[[ $# -lt 5 ]] && {
        echo Usage: `basename $0` 'userid password database tenant_code host [account_id1] [account_id2] ... > out_file_path'
        exit 1
}

AUTH="-u $1 -p$2"
DATABASE="$3 -h $5"
TENANT_CODE="'$4'"
shift; shift; shift; shift; shift;
separator=
ACCOUNT_ID=
for i in $*
do
        ACCOUNT_ID="$ACCOUNT_ID$separator '$i'"
        separator=,
done

MYSQLDUMP="mysqldump"

SEED_TABLES="tenants sequences enumerations configuration_properties countries states districts cities holidays currencies roles role_limits report_links users authorities banks branches branchsets branchset_branches user_assignments accountid_generation_rules tax_rates products gl_subheads account_codes internal_settings fee_computation_rules fee_computation_slabs interest_tables interest_table_slabs provisioning_tables provisioning_slabs loan_od_products loan_od_product_fees casa_products casa_product_fees deposit_products purchase_sale_products entity_attributes"
CUSTOMER_TABLES="customers customer_locations customer_working_registers customer_limits customer_collaterals"
ACCOUNT_TABLES="account_profiles account_balances account_holders account_ach_mandates account_pdc general_transactions fees fee_payments fee_accruals interest_computations loan_od_profiles loan_od_working_registers loan_od_disbursements loan_od_scheduled_disbursements loan_od_demands loan_od_repayments npa_transactions loan_od_write_offs loan_od_history_transactions loan_od_moratoriums loan_od_amendments loan_od_provisioning loan_od_tags loan_od_history_tags loan_od_linked_accounts loan_od_fulfillments loan_od_collaterals loan_od_lender_working_registers"
ENTITY_TABLES="ach_mandates"
MAKER_CHECKER_MASTER_TABLES="maker_checker_master_actions"
MAKER_CHECKER_TRANSACTION_TABLES="maker_checker_transactions"

$MYSQLDUMP $AUTH $DATABASE --no-data --single-transaction=TRUE | sed "/DEFINER=/d"
$MYSQLDUMP $AUTH $DATABASE --no-create-info --single-transaction=TRUE $SEED_TABLES

echo "INSERT INTO BATCH_STEP_EXECUTION_SEQ (ID, UNIQUE_KEY) select * from (select 0 as ID, '0' as UNIQUE_KEY) as tmp where not exists(select * from BATCH_STEP_EXECUTION_SEQ);"
echo "INSERT INTO BATCH_JOB_EXECUTION_SEQ (ID, UNIQUE_KEY) select * from (select 0 as ID, '0' as UNIQUE_KEY) as tmp where not exists(select * from BATCH_JOB_EXECUTION_SEQ);"
echo "INSERT INTO BATCH_JOB_SEQ (ID, UNIQUE_KEY) select * from (select 0 as ID, '0' as UNIQUE_KEY) as tmp where not exists(select * from BATCH_JOB_SEQ);"

[ -z "$ACCOUNT_ID" ] && {
  echo -- Account Dump successful
  exit 0; # only seed to be extracted
}

$MYSQLDUMP $AUTH $DATABASE --no-create-info $CUSTOMER_TABLES --single-transaction=TRUE --where "tenant_code = $TENANT_CODE and customer_id in (select distinct customer_id from account_holders where account_id in ( $ACCOUNT_ID ) and tenant_code = $TENANT_CODE)"
$MYSQLDUMP $AUTH $DATABASE --no-create-info $ACCOUNT_TABLES --single-transaction=TRUE --where="account_id in ( $ACCOUNT_ID ) and tenant_code = $TENANT_CODE"
$MYSQLDUMP $AUTH $DATABASE --no-create-info $MAKER_CHECKER_TRANSACTION_TABLES --single-transaction=TRUE --where="entity_type = 'LoanTransaction' and entity_id in ( $ACCOUNT_ID ) and tenant_code = $TENANT_CODE"
$MYSQLDUMP $AUTH $DATABASE --no-create-info $ENTITY_TABLES --single-transaction=TRUE --where "tenant_code = $TENANT_CODE and entity_type>0 and entity_id in (select distinct customer_id from account_holders where account_id in ( $ACCOUNT_ID ) and tenant_code = $TENANT_CODE)"
$MYSQLDUMP $AUTH $DATABASE --no-create-info $ENTITY_TABLES --single-transaction=TRUE --where "tenant_code = $TENANT_CODE and entity_type=0 and entity_id in ( $ACCOUNT_ID )"

# This script does not dump all internal account entries as they are across accounts and cannot be identified by the provided account_id
# Note that the following is to be uncommented if referencing account entries are also to be dumped; by default only the entries of the loan account are dumped
$MYSQLDUMP $AUTH $DATABASE account_entries --where="account_id in ($ACCOUNT_ID) and tenant_code = $TENANT_CODE"
#$MYSQLDUMP $AUTH $DATABASE account_entries --where="(account_id in ($ACCOUNT_ID) or referenced_account_id in ($ACCOUNT_ID)) and tenant_code = $TENANT_CODE"

echo -- Account Dump successful
exit 0


