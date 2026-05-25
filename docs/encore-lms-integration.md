# Encore LMS integration (LOS 2.0)

## Vendor vs LOS-owned code

| Location | What it is |
|----------|------------|
| `external-services/encore-server` | **Official vendor Encore** server (JAR + `encoresite/`). Build/run **unchanged** — see vendor `Dockerfile`. |
| `external-services/encore-client` | **Official vendor** web UI (static assets). Built and served by nginx (`encore-client` service in compose). |
| `services/los-core-service` (`com.los.lms.*`, `com.los.encore.client.*`) | **LOS** integration only: REST `/api/v1/lms/**`, LMS tables on **`los_core`** DB, HTTP client to vendor Encore via `los.lms.encore.*`. This is **not** a fork of vendor Encore. |

Import instructions: `external-services/README.md`.

## Configuration

### Vendor Encore (Docker / infra)

- MySQL for vendor app: **`encore-mysql`** (default root password `encoreroot` unless overridden — keep healthcheck in sync).
- Vendor API port **8090** (see vendor `encoresite/conf/application-uat.yml`).

### LOS `los-core-service` → vendor HTTP

| Variable | Purpose |
|----------|---------|
| `ENCORE_BASE_URL` | Vendor Encore base URL (e.g. `https://core.dev.billionloans.com/encore/` on Docker network, or `http://10.0.1.7:8090/encore/` when pointing at existing infra). |
| `ENCORE_API_USERNAME` / `ENCORE_API_PASSWORD` | Basic Auth to vendor webservices. |
| `VENDOR_ENCORE_BASE_URL` | Optional override in compose for the same value as `ENCORE_BASE_URL`. |
| `LMS_ENCORE_SYNC_ENABLED` / `LMS_ENCORE_SYNC_CRON` | Scheduled summary sync (`EncoreSummarySyncJob` in los-core). |
| `LMS_CALLBACK_HMAC_SECRET` | Optional HMAC for repayment callback. |

Spring properties mirror the former adapter: `los.lms.encore.*` and `los.lms.callback.*` in `application.yml`.

Legacy flat `encore.*` keys are documented in `external-services/config/encore.properties.example` for mapping to `los.lms.encore.*`.

## LOS core

`los-core-service` hosts LMS controllers and calls **vendor Encore** over HTTP (`EncoreHttpTransport` / `DefaultEncoreLmsApi`). There is **no** separate LMS microservice.

## Gateway

Public LMS routes target **`lb://los-core-service`** (route id `encore-lms`, path `/api/v1/lms/**`).

## Legacy mapping

See [lms-integration-mapping.md](lms-integration-mapping.md).
