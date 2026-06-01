# Host nginx — LOS at `/los/`

LOS UI is built and served under **`/los/`** (Vite `base`, React Router `basename`, API **`/los/api/v1`**).

## EC2 setup (keeps existing nginx config)

1. Rebuild LOS UI after pull:
   ```bash
   cd /vol/LOS_APP
   git pull origin los_plp_integration
   docker compose -f docker-compose.prod.yml up -d --build ui-service
   ```

2. Copy snippet (locations only):
   ```bash
   sudo cp deploy/nginx/los-host-locations.conf /etc/nginx/snippets/los-host-locations.conf
   ```

3. Edit **`/etc/nginx/sites-available/default`** — inside the existing `server { ... }` for `credinnov-sandbox.senseitech.com`, add **one line** (do not change other `location` blocks):
   ```nginx
   include snippets/los-host-locations.conf;
   ```

4. Reload:
   ```bash
   sudo nginx -t && sudo systemctl reload nginx
   ```

5. Test:
   ```bash
   curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/los/
   curl -s -o /dev/null -w "%{http_code}\n" -H "Host: credinnov-sandbox.senseitech.com" http://127.0.0.1/los/
   ```

Browser: `http://credinnov-sandbox.senseitech.com/los/`
