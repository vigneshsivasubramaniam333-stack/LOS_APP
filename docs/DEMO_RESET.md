# Demo data reset (LOS)

## UAT configuration

Set on `los-core-service`:

```bash
LOS_DEMO_ENABLED=true
```

Default is `false`. Do not enable in production.

Verify: `GET /api/v1/demo/status` → `"demoEnabled": true`.

## What the reset does (LOS only)

- Deletes all `loan_applications` and dependent rows (documents, KYC, e-sign, etc.).
- Deletes auto-provisioned `BORROWER` users from `los_users` (seed borrowers preserved via `los.demo.preserved-user-emails`).
- Deletes **LOS-local PLP master cache**: `sub_program_masters`, `program_masters`, `anchor_masters` (rows synced from or created for PLP integration).

**Does not** call the remote PLP app. Use **Reset demo data** on the PLP platform to clear programs, loans, and portal users there.

**Does not** remove staff seed users or workflow configuration.

## API

- `GET /api/v1/demo/status` — whether demo mode is on
- `DELETE /api/v1/demo/applications` — full LOS purge (404 when demo mode is off)

## PLP data

| Data | Cleared by |
|------|------------|
| PLP programs, loans, borrowers (remote) | PLP app **Reset demo data** |
| PLP program/anchor/sub-program rows on LOS DB | LOS app **Reset demo data** |
