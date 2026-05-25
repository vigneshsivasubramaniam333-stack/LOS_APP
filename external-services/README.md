# Vendor Encore (LMS) — external services

This directory holds the **official vendor Encore** applications **unchanged** from your Docker project. They are **not** part of the LOS Maven reactor and are **not** rewritten by LOS.

## 1. Import the vendor trees (required before `docker compose build`)

From a shell on the machine that has your vendor checkout (example paths):

**Windows (PowerShell):**

```powershell
$root = Join-Path $PSScriptRoot "external-services"
robocopy "D:\ENCORE\DOCKER-PROJECT\encore-server" "$root\encore-server" /E /XD target .git
robocopy "D:\ENCORE\DOCKER-PROJECT\encore-client" "$root\encore-client" /E /XD target .git
```

**Linux / macOS:**

```bash
rsync -a --exclude target --exclude .git /path/to/DOCKER-PROJECT/encore-server/ external-services/encore-server/
rsync -a --exclude target --exclude .git /path/to/DOCKER-PROJECT/encore-client/ external-services/encore-client/
```

The vendor **encore-server** image expects (per vendor `Dockerfile`):

- `encore-boot-server-2026.05.01-mysql-local-nokafka-native.jar`
- `encoresite/` directory
- helper scripts next to the JAR

Do not replace those with LOS code.

## 2. What runs where

| Path | Role |
|------|------|
| `external-services/encore-server` | Vendor Spring Boot server (JAR + `encoresite/`). Default API port **8090** (see vendor `encoresite/conf/application-uat.yml`). |
| `external-services/encore-client` | Vendor web UI (static assets + `config/config.json`). Docker image builds from this folder (**`encore-client`** service — nginx `nginx:stable`, root `/usr/share/nginx/encoreclient`). Host port **9080** in dev compose. |

## 3. LOS integration (not vendor code)

| Component | Role |
|-----------|------|
| **`los-core-service`** (`com.los.lms.*`, `com.los.encore.client.*`) | LOS-owned **integration only**: `/api/v1/lms/**`, LMS tables on **`los_core`** DB, callbacks. Calls vendor Encore over HTTP using `ENCORE_BASE_URL` (default in compose: `https://core.dev.billionloans.com/encore/`). |

Legacy-style flat properties are captured in `config/encore.properties.example` for mapping to `los.lms.encore.*` / environment variables.

## 4. MySQL healthcheck note

Default MySQL root password in compose examples is **`encoreroot`**. If you set `ENCORE_MYSQL_ROOT_PASSWORD` to something else, align the MySQL healthcheck in `docker-compose.yml` or use the same value in both places.

## 5. Docker

Root `docker-compose.yml` / `docker-compose.dev.yml` / `docker-compose.prod.yml` define:

- `encore-mysql` — database for vendor Encore server  
- `encore-server` — build `external-services/encore-server`  
- `encore-client` — build `external-services/encore-client` (vendor static tree + nginx)
