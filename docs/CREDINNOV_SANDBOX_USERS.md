# Credinnov sandbox — login users

**Branch:** `credinnov`  
**Password (all users):** `Bltest@123`  
**LOS URL:** `http://credinnov-sandbox.senseitech.com/los/`  
**PLP platform:** `http://credinnov-sandbox.senseitech.com/plp/`  
**PLP anchor:** `http://credinnov-sandbox.senseitech.com/plp-anchor/`  
**PLP borrower:** `http://credinnov-sandbox.senseitech.com/plp-borrower/`

Billionloans demo users are **deactivated** when migration `V57__seed_credinnov_los_auth_users.sql` (LOS) / `V3__seed_credinnov_users.sql` (PLP) runs.

## LOS (`los_users`)

| Display name | Email | Role |
|--------------|-------|------|
| Admin | admin@credinnov.com | Administrator |
| Credit Manager | creditmanager@credinnov.com | Credit Manager |
| Credit Officer | creditofficer@credinnov.com | Credit Officer |
| Credit Officer 2 | creditofficer2@credinnov.com | Credit Officer |
| Sales | sales@credinnov.com | Sales |
| Accounts | accounts@credinnov.com | Accounts |
| Borrower | borrower@credinnov.com | Borrower |

## PLP (`plp_iam.users`)

| Display name | Email | PLP role | Portal |
|--------------|-------|----------|--------|
| Admin | admin@credinnov.com | PLATFORM_ADMIN | `/plp/` |
| Credit Manager | creditmanager@credinnov.com | CREDIT_MANAGER | `/plp/` |
| Credit Officer | creditofficer@credinnov.com | CREDIT_ANALYST | `/plp/` |
| Credit Officer 2 | creditofficer2@credinnov.com | CREDIT_ANALYST | `/plp/` |
| Sales | sales@credinnov.com | COMPLIANCE_OFFICER | `/plp/` |
| Accounts | accounts@credinnov.com | ACCOUNTS_OFFICER | `/plp/` |
| Anchor Admin | anchor@credinnov.com | ANCHOR_ADMIN | `/plp-anchor/` |
| Borrower | borrower@credinnov.com | BORROWER | `/plp-borrower/` |

## Deploy on EC2

```bash
# LOS
cd /vol/LOS_APP && git fetch && git checkout credinnov && git pull origin credinnov
docker compose -f docker-compose.prod.yml up -d --build los-core ui-service

# PLP
cd /vol/PLP-APP && git fetch && git checkout credinnov && git pull origin credinnov
docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d --build iam-service
docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d platform-ui anchor-portal borrower-portal
```

Flyway runs migrations on service startup. To re-run on existing DB, restart `los_core` and `plp-iam` (or run SQL manually if migrations already applied).
