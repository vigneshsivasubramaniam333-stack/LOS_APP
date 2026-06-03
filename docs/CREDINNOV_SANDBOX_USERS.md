# Credinnov sandbox — login users

**Branch:** `credinnov`  
**Password (all users):** `Bltest@123`  
**LOS URL:** `http://credinnov-sandbox.senseitech.com/los/`  
**PLP platform:** `http://credinnov-sandbox.senseitech.com/plp/`  
**PLP anchor:** `http://credinnov-sandbox.senseitech.com/plp-anchor/`  
**PLP borrower:** `http://credinnov-sandbox.senseitech.com/plp-borrower/`  
**Encore LMS UI:** `http://credinnov-sandbox.senseitech.com/encore-client/` (`admin` / `password1` — same as `.env.prod`)

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

## PLP login / network error

Browsers must **not** call `http://localhost:8180` from the public site. Docker UIs mount `frontend/packages/*/docker/env-config.js`, which must use **`/plp-api`** when the hostname is `credinnov-sandbox.senseitech.com` (requires nginx `location /plp-api/`).

After updating env-config, restart PLP UI containers (no rebuild required):

```bash
docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d platform-ui anchor-portal borrower-portal
```

Test API via nginx:

```bash
curl -s -X POST http://127.0.0.1/plp-api/api/v1/auth/login \
  -H 'Host: credinnov-sandbox.senseitech.com' \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@credinnov.com","password":"Bltest@123"}'
```

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

## LOS → PLP anchor sync (503 on `/integrations/los/anchors`)

LOS logs in to PLP, then calls **`POST /api/v1/integrations/los/anchors`**, which the gateway routes to **`program-service`**.

| Symptom | Cause |
|---------|--------|
| Login 200, anchor sync **503** | `plp-program` not registered in Eureka or still starting |
| LOS uses `admin@plp.com` | Set `PLP_INTEGRATION_EMAIL=admin@credinnov.com` in `.env.prod` and restart `los_core` |

On EC2:

```bash
# PLP program service must be up
docker ps --filter name=plp-program
docker logs plp-program --tail 80
curl -s http://127.0.0.1:8182/actuator/health

# Direct anchor API test (after PLP login token)
TOKEN=$(curl -s -X POST http://127.0.0.1:8180/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@credinnov.com","password":"Bltest@123"}' | jq -r .accessToken)
curl -s -X POST http://127.0.0.1:8180/api/v1/integrations/los/anchors \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"sourceSystem":"LOS","losAnchorId":"00000000-0000-0000-0000-000000000099","anchor":{"name":"Test","code":"T-ANC-01","pan":"AAAAA0000A"}}'

# LOS must reach PLP gateway from inside the los_core container.
cd /vol/LOS_APP
# Preferred on EC2 (same Docker host as PLP):
docker compose -f docker-compose.prod.yml -f docker-compose.plp-integration.yml up -d --build los-core

# Verify from inside los_core (should return {"status":"UP"}):
docker exec los_core wget -qO- http://api-gateway:8080/actuator/health

# If plp_plp-net is missing: cd /vol/PLP-APP && docker compose up -d first, then docker network ls | grep plp
```

## Encore LMS (LOS → remote API)

LOS calls the Encore **webservices** API (`/encore/...`), not the browser UI at `/encore-client/`. On Credinnov EC2 the team server is proxied as `/credinnov-encore-server/` → `127.0.0.1:8091`.

In `/vol/LOS_APP/.env.prod` (credentials unchanged):

```bash
ENCORE_BASE_URL=http://credinnov-sandbox.senseitech.com/credinnov-encore-server/encore/
ENCORE_API_USERNAME=admin
ENCORE_API_PASSWORD=password1
```

Restart after edit: `docker compose -f docker-compose.prod.yml up -d --no-deps los-core`

Smoke test from `los_core`:

```bash
docker exec los_core wget -qO- --user=admin --password=password1 \
  "http://credinnov-sandbox.senseitech.com/credinnov-encore-server/encore/webservices/loans/accounts/findBankWorkingDate"
```

Public equivalent: `http://credinnov-sandbox.senseitech.com/credinnov-encore-server/encore/`
