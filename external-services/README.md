# External services

LOS integrates with **Encore LMS over remote HTTP** from `los-core-service` (`com.los.encore.client`).

Configure via environment variables (see `.env.example`):

- `ENCORE_BASE_URL` — Encore API base URL (must end with `/encore/` when required by your environment)
- `ENCORE_API_USERNAME`
- `ENCORE_API_PASSWORD`

Local vendor Encore Docker images (`encore-server`, `encore-client`, `encore-mysql`) are **not** part of this stack.

LOS UI in production uses host port **8080** by default (see `docs/deployment-port-mapping.md`).
