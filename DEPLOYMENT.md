# Deployment — Atlan MFO Dashboard

Guide for installing the application at a client site (multi-family office):
centralized database, user accounts, per-machine installers, security and
backups. Cross-cutting decisions: spec §13.

---

## Quick start — production (managed database + fixed office IP)

Chosen scenario: **managed** PostgreSQL (EU region), **allowlist of the
office's public IP**, **TLS**, **Windows** machines via CI. Details in the
sections below.

1. **Office IP** — from the office: `curl ifconfig.me` (confirm with the ISP that it is a *static* IP).
2. **Managed instance** — create PostgreSQL 16 (EU region), allowlist the office IP as `/32`, note host/port + download the **CA certificate**.
3. **Initialize the database** (once, from an allowlisted machine; edit `db/roles.sql` first):
   ```bash
   psql -f src/main/resources/db/schema.sql
   psql -f src/main/resources/db/roles.sql
   psql -f src/main/resources/db/seed-prod.sql
   ```
4. **Accounts**:
   ```bash
   scripts/user-add.sh <username> "<temporary password>" "<Full name>" ANALYST|PARTNER
   ```
5. **Windows installer** — `git tag v1.0.0 && git push origin v1.0.0` → download the `.msi` from the GitHub Actions run artifacts.
6. **Machine** — install the `.msi`, drop a `config.properties` (`sslmode=verify-full` + CA, `db.user=atlan_app`, `db.runMigrations=false`, `db.seed=none`).
7. **First login** — temporary password → change enforced.

> Sequence validated in a dry run (steps 3-4): schema + role with minimal
> privileges (`SELECT/INSERT/UPDATE/DELETE`, no superuser or DDL) + admin-only
> + accounts.

---

## 1. Overview

```
   Analyst machine ─┐
   Partner machine ─┼──(network, TLS)──▶  Centralized PostgreSQL (single database)
   Analyst machine ─┘
```

- **A single database** shared by all machines (data is common to everyone).
- **The application** is installed on each machine (self-contained installer,
  embedded Java runtime: no JDK to install).
- **Application-level multi-user**: everyone has their own account in
  `app_user` (BCrypt password), with an `ANALYST` role (read/write) or
  `PARTNER` role (read-only, locked into presentation mode).
- The application connects with **a single database role** (`atlan_app`) with
  minimal privileges — never a superuser.

---

## 2. Choosing where to host the database

| Option | When to choose it | Examples |
|---|---|---|
| **Managed PostgreSQL (cloud)** | Simplicity: TLS, backups and updates managed for you | Neon, Supabase, AWS RDS, Azure Database for PostgreSQL, DigitalOcean |
| **On-premise server** | Data must stay at the client's site | Internal VM / server, under their control |

In both cases:

- **Never expose port 5432 in the clear on the Internet.** Restrict access
  via VPN, SSH tunnel, or IP allowlist.
- **TLS mandatory** on the client side (`?sslmode=require` in the JDBC URL).

---

## 3. Installing the database (one time)

On the server / managed instance, with a PostgreSQL **administrator**
account:

```bash
# 1. Create the database
createdb atlan_mfo            # or via the managed provider's console

# 2. Apply the schema (structure) — from a copy of the repository
psql -d atlan_mfo -f src/main/resources/db/schema.sql

# 3. Create the application role with minimal privileges
#    (edit db/roles.sql first: replace CHANGE_THIS_PASSWORD)
psql -d atlan_mfo -f src/main/resources/db/roles.sql

# 4. Create the initial application administrator account (no demo data)
psql -d atlan_mfo -f src/main/resources/db/seed-prod.sql
```

After this step:

- The database role `atlan_app` exists (read/write only).
- An application account `admin` exists with a temporary password
  (`Atlan-Setup-2026`) and is required to change it on first login.

> In production, the application runs with `db.runMigrations=false`: the
> schema is not replayed on startup (it was applied here, by hand, by the
> database owner).

---

## 4. Creating user accounts

From a copy of the repository, with a `config.properties` pointing to the
central database, provision each person (the password is temporary; the user
changes it on first login):

```bash
scripts/user-add.sh jdupont "TemporaryPassword1" "Jean Dupont" ANALYST
scripts/user-add.sh mlefevre "TemporaryPassword2" "Marie Lefevre" PARTNER
```

The tool hashes the password with BCrypt and inserts/refreshes the account.
Re-run the same command to **reset** an existing account's password.

---

## 5. Installing the application on each machine

### 5.1 Building the installer (once per target platform)

```bash
scripts/package.sh dmg     # macOS  → target/installer/*.dmg
scripts/package.sh msi     # Windows (requires WiX Toolset) → *.msi
```

The installer embeds a reduced Java runtime: **nothing else to install** on
the machine.

### 5.2 Signing (recommended, otherwise security warnings)

- **macOS**: sign with a *Developer ID Application* certificate, then
  **notarize** with Apple, otherwise Gatekeeper shows "unidentified
  developer".
  ```bash
  export MAC_SIGN_IDENTITY="Developer ID Application: Your Name (TEAMID)"
  scripts/package.sh dmg
  # then notarization:
  xcrun notarytool submit "target/installer/Atlan MFO Dashboard-1.0.0.dmg" \
      --apple-id "you@example.com" --team-id "TEAMID" --password "<app password>" --wait
  xcrun stapler staple "target/installer/Atlan MFO Dashboard-1.0.0.dmg"
  ```
- **Windows**: sign the `.msi` with `signtool` and a code-signing
  (Authenticode) certificate.

### 5.3 Configuring the machine

Create a `config.properties` in the user profile — a stable location, read
regardless of how the app is launched (Start menu / Dock) and editable
without administrator rights:

- **Windows**: `%USERPROFILE%\.atlan-mfo\config.properties`
- **macOS / Linux**: `~/.atlan-mfo/config.properties`

Point it at the central database, over TLS:

```properties
db.url=jdbc:postgresql://db.client.internal:5432/atlan_mfo?sslmode=require
db.user=atlan_app
db.password=<strong password for the atlan_app role>
db.runMigrations=false
db.seed=none
```

> The app also reads a `config.properties` in the current working directory
> (handy in development), and the `ATLAN_DB_URL` / `ATLAN_DB_USER` /
> `ATLAN_DB_PASSWORD` / `ATLAN_DB_SEED` / `ATLAN_DB_RUN_MIGRATIONS` environment
> variables, which take priority over the file. **In production always set
> `db.runMigrations=false` and `db.seed=none`** so an installed machine never
> replays the schema or inserts demo data into the shared database.

### 5.4 First login

The user opens the application, logs in with their username and temporary
password, and the application **immediately enforces** a password change
(§13.3).

---

## 6. Backups

- **Managed**: enable the provider's automatic backups (point-in-time
  recovery if available).
- **On-premise**: schedule a regular `pg_dump`, e.g. daily:
  ```bash
  pg_dump -Fc atlan_mfo > /backups/atlan_mfo_$(date +%F).dump
  ```
  and regularly test restoring it (`pg_restore`).

---

## 7. Resetting the pipeline after an investment committee meeting

If the pipeline needs to start fresh after each IC meeting (a clean slate for
the next cycle), use:

```bash
scripts/reset-pipeline.sh
```

The script shows the current state (number of funds, vintages, deals,
users), asks you to type `RESET` to confirm, then purges **only** the
pipeline tables (`fund_investment`, `fund_vintage`, `direct_deal`). User
accounts (`app_user`) and the schema are kept — no need to recreate access
each cycle.

**Automatic backup.** Before purging, the script writes a restorable
`pipeline-backup-<timestamp>.sql` file (INSERT statements for the three
tables) to the working directory. Keep it if the cycle's history matters; to
restore, run `psql <connection> -f pipeline-backup-<timestamp>.sql`. This is
self-contained (no `pg_dump` required). For a full database backup (including
accounts), a `pg_dump` (see §6) remains the reference.

> The reset **does not** touch the `opportunity_outcome` table (predicted vs.
> realized calibration): that dataset is meant to accumulate across cycles.

---

## 8. Updating the application

1. Bump the version (`pom.xml`, `PKG_VERSION` in `scripts/package.sh`).
2. Rebuild and re-sign the installers, redistribute them.
3. If the **schema** changes: apply the new migrations on the central
   database **once**, by the owner, before deploying the new version to the
   machines. `schema.sql` is idempotent (`CREATE TABLE IF NOT EXISTS`), so
   re-running it only adds what is missing:
   ```bash
   psql <connection as owner> -f src/main/resources/db/schema.sql
   ```
   Recent additions requiring this: `scoring_param` (editable methodology) and
   `opportunity_outcome` (predicted vs. realized calibration). Until applied,
   those features degrade gracefully (defaults / empty view) instead of failing.

---

## 9. Security summary

- `atlan_app` database role with minimal privileges (no DDL, no superuser).
- TLS mandatory; database port not exposed publicly (VPN / allowlist).
- Application passwords hashed with BCrypt; change forced on first login.
- No demo data in production (`db.seed=none`).
- `config.properties` contains secrets → **never committed** (already in
  `.gitignore`), distributed securely to each machine.

> **Known limitation.** The application talks directly to the database: the
> `atlan_app` role's credentials therefore live on every machine. For a small
> trusted team, over VPN/TLS, this is acceptable. For stronger security, the
> next step would be to insert a **backend API** between the application and
> the database, so that secrets and logic stay server-side.

---

## 10. Hosting recommendation (private data)

"Private" does not require "on-premise." A **well-configured managed
PostgreSQL** is often safer than a self-managed office server (patches,
encryption and backups handled by the provider).

**Recommended: managed PostgreSQL**, with a region in the client's
jurisdiction (data residency), encryption at rest, TLS, **IP allowlist** and
automatic backups. Providers with allowlist + EU region: Azure Database for
PostgreSQL, AWS RDS, Scaleway, OVHcloud, Neon.

**On-premise** only if a contractual/regulatory constraint requires that
data never leave the premises.

### IP allowlist — a condition for it to work

- **Machines with a fixed IP (office)** → allowlist the office's public IP. Simple and robust.
- **Mobile machines (changing IP)** → the allowlist blocks them: plan for a
  **corporate VPN with a fixed exit IP** (allowlist the VPN's IP), or route
  connections through the office network.

---

## 11. Building Windows installers

`jpackage` only produces a package **for the OS it runs on**: you cannot
build a Windows `.msi` from a Mac. Two options:

1. **CI (recommended)** — the [`.github/workflows/package.yml`](.github/workflows/package.yml)
   workflow automatically builds the Windows (`.msi`) and macOS (`.dmg`)
   installer on GitHub runners, on every version tag:
   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```
   The installers are then downloadable from the run's artifacts.

2. **Windows machine** — install a JDK 21+ and **WiX Toolset 3.x**, then:
   ```bash
   scripts/package.sh msi
   ```

### Windows signing (Authenticode)

To avoid the SmartScreen warning, sign the `.msi` with a code-signing
certificate:
```
signtool sign /fd SHA256 /a /tr http://timestamp.digicert.com /td SHA256 "Atlan MFO Dashboard-1.0.0.msi"
```
In CI, add the certificate as a secret and a `signtool` step after the build.
