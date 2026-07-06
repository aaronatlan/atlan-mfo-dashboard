# Atlan MFO Dashboard

Desktop application (JavaFX) for tracking a multi-family office's investment
pipeline. See the full specification: [`atlan-mfo-dashboard-spec.md`](atlan-mfo-dashboard-spec.md).

## Requirements

- JDK 21+ (tested on JDK 25, compiled targeting Java 21)
- Maven 3.9+
- PostgreSQL 15+

## Configuration

The connection is provided either via environment variables, or via a local
`config.properties` file (never committed):

```bash
cp config.properties.example config.properties
# then fill in db.url / db.user / db.password
```

Equivalent environment variables (take priority):
`ATLAN_DB_URL`, `ATLAN_DB_USER`, `ATLAN_DB_PASSWORD`.

## Database

Create the database once:

```bash
createdb atlan_mfo
```

On startup, if `db.runMigrations=true`, the application runs
`src/main/resources/db/schema.sql` then the seed for the profile chosen via
`db.seed` (`dev` = demo data, `prod` = admin only, `none` = none). All scripts
are idempotent.

## Run

```bash
mvn clean javafx:run
```

## First login (development seed, `db.seed=dev`)

The **dev** seed creates two demo accounts:

- `admin` / `admin` — analyst; `must_change_password`: the application forces
  a password change before accessing the screens (see spec §13.3);
- `partner` / `partner` — read-only, locked into presentation mode (§6.3, §7).

In **production** (`db.seed=prod`), only an `admin` account with a temporary
password is created; other users are provisioned via `scripts/user-add.sh`
(see [DEPLOYMENT.md](DEPLOYMENT.md)).

## Tests

```bash
mvn test
```

## Packaging (distribution)

Produces a self-contained native package (Java runtime embedded via
`jlink`/`jpackage`; no JDK required on the target machine), see §13.5:

```bash
scripts/package.sh app-image     # portable bundle (.app / folder)
scripts/package.sh dmg           # macOS installer
scripts/package.sh msi           # Windows installer (requires WiX)
```

The result is written to `target/installer/`.

> **macOS + iCloud**: if the repository is in an iCloud-synced folder
> (Desktop, Documents), `jpackage`'s ad-hoc signing fails because of the
> `com.apple.FinderInfo` extended attribute. Cloning/building from a folder
> outside iCloud (e.g. `~/dev/`) resolves the issue.

### Fonts

The theme uses **Inter** and **Newsreader**. They are loaded from
`src/main/resources/fonts/` if present (`Inter-Regular.ttf`,
`Inter-SemiBold.ttf`, `Newsreader-Regular.ttf`), otherwise falling back
cleanly to the system fonts. Dropping the static TTF files into this folder
is enough to bundle them.

## Progress

- **Phase 0 — Foundations**, **1 — Read**, **2 — Scoring**, **3 — Data entry**,
  **4 — Modes & roles**, **5 — Polish**, **6 — Distribution**: shipped.

Detailed roadmap: spec §10; cross-cutting decisions: §13.
