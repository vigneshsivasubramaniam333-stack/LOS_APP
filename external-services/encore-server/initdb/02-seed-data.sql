-- ============================================================================
-- Encore LMS – Seed data for fresh database initialization
-- Runs via MySQL docker-entrypoint-initdb.d on FIRST START ONLY
-- (when /var/lib/mysql is empty / volume is new).
--
-- Matches application-uat.yml:  application.tenants.MG.pattern = encore
-- Matches .env.prod:            ENCORE_API_USERNAME=vuser  ENCORE_API_PASSWORD=vuser
-- ============================================================================

USE `encoredb`;

-- ── Tenant ──────────────────────────────────────────────────────────────────
INSERT INTO `tenants` (`id`, `created_at`, `created_by`, `version`, `tenant_code`, `tenant_name`)
SELECT 1, NOW(6), 'system', 0, 'MG', 'MG Tenant'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tenants` WHERE `tenant_code` = 'MG');

-- ── Default currency (INR) ──────────────────────────────────────────────────
INSERT INTO `currencies` (`id`, `created_at`, `created_by`, `version`, `tenant_code`,
                           `currency_code`, `currency_name`, `fraction_digits`, `integer_digits`)
SELECT 1, NOW(6), 'system', 0, 'MG', 'INR', 'Indian Rupee', 2, 10
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `currencies` WHERE `tenant_code` = 'MG' AND `currency_code` = 'INR');

-- ── Head-office branch ──────────────────────────────────────────────────────
INSERT INTO `branches` (`id`, `created_at`, `created_by`, `version`, `tenant_code`,
                         `branch_code`, `branch_name`, `branch_open_date`, `is_hq`, `operational_status`)
SELECT 1, NOW(6), 'system', 0, 'MG', 'HQ', 'Head Office', CURDATE(), 1, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `branches` WHERE `tenant_code` = 'MG' AND `branch_code` = 'HQ');

-- ── Admin role ──────────────────────────────────────────────────────────────
INSERT INTO `roles` (`id`, `created_at`, `created_by`, `version`, `tenant_code`,
                      `role_code`, `role_name`, `description`)
SELECT 1, NOW(6), 'system', 0, 'MG', 'ADMIN', 'Administrator', 'Full access role'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `roles` WHERE `tenant_code` = 'MG' AND `role_code` = 'ADMIN');

-- ── Authority entry for the admin role ──────────────────────────────────────
INSERT INTO `authorities` (`id`, `created_at`, `created_by`, `version`, `tenant_code`,
                            `authority_code`, `role_code`)
SELECT 1, NOW(6), 'system', 0, 'MG', 'ALL', 'ADMIN'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `authorities` WHERE `tenant_code` = 'MG' AND `role_code` = 'ADMIN');

-- ── API / admin user (vuser) — password is BCrypt hash of "vuser" ───────────
-- BCrypt('vuser') = $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
INSERT INTO `users` (`id`, `created_at`, `created_by`, `version`, `tenant_code`,
                      `userid`, `name`, `password`, `active`, `reset_password`,
                      `otp_auth_applicable`, `consecutive_failure_attempts`)
SELECT 1, NOW(6), 'system', 0, 'MG',
       'vuser', 'API User', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 1, 0, 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `tenant_code` = 'MG' AND `userid` = 'vuser');

-- ── Assign vuser → ADMIN role at HQ branch ──────────────────────────────────
INSERT INTO `user_assignments` (`id`, `created_at`, `created_by`, `version`, `tenant_code`,
                                 `userid`, `role_code`, `branch_code`, `scope`,
                                 `customer_user`, `virtual_user`)
SELECT 1, NOW(6), 'system', 0, 'MG',
       'vuser', 'ADMIN', 'HQ', 'read,write', 0, 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `user_assignments`
    WHERE `tenant_code` = 'MG' AND `userid` = 'vuser' AND `role_code` = 'ADMIN'
);
