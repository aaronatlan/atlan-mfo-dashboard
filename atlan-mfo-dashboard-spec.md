# Atlan MFO Dashboard — Technical Specification

> Reference document for development with Claude Code.
> Investment pipeline tracking application for a multi-family office (MFO).
> Goal: replace a manual Excel-based tracker with a structured desktop application, with automatic scoring and two viewing modes.

---

## 1. Goal and scope

Atlan is a multi-family office that continuously evaluates private-market investment opportunities (funds and direct deals). Tracking is currently done by hand in Excel: repetitive data entry, fragile formulas, no access control, no clean rendering for the investment committee (IC).

The application must:

- Centralize all pipeline opportunities in a shared database.
- Automate the score calculation for each opportunity according to a fixed methodology (3 grids).
- Recalculate the score **live** while data is being entered.
- Offer two distinct surfaces:
  - **Analyst view** — dense, editable, the daily working surface.
  - **Presentation mode** — clean, read-only, for projecting in investment committee.
- Support multiple users with roles (analyst / partner).

The scope covers four families of opportunities, split across two data structures:

| Section | Data structure | Scoring grid |
|---|---|---|
| Buyout, growth, VC | Fund | A |
| Secondaries | Fund (same schema as Buyout/growth/VC) | A |
| Private credit | Fund (same schema) | B |
| Co-investment and direct | Deal (distinct schema) | C |

---

## 2. Technical stack

Locked-in decisions:

- **Language**: Java 21 (LTS).
- **UI**: JavaFX 21, with FXML for screen structure and a JavaFX CSS stylesheet for the theme.
- **Database**: PostgreSQL 15+.
- **Data access**: plain JDBC via an explicit DAO layer, with **HikariCP** for connection pooling. No Hibernate/JPA — readable, transparent SQL, easier for a third party to audit.
- **Build**: Maven.
- **Password hashing**: BCrypt (`at.favre.lib:bcrypt` library).
- **Tests**: JUnit 5, with priority coverage of the scoring engine.

The application connects to PostgreSQL with **a single application role** (credentials in configuration, never hard-coded or committed). Multi-user support is handled **at the application level** via an `app_user` table, not via per-person PostgreSQL roles. This avoids distributing database credentials to every machine.

### Main Maven dependencies

- `org.openjfx:javafx-controls:21`
- `org.openjfx:javafx-fxml:21`
- `org.postgresql:postgresql` (JDBC driver)
- `com.zaxxer:HikariCP`
- `at.favre.lib:bcrypt`
- `org.junit.jupiter:junit-jupiter` (test)
- `org.openjfx:javafx-maven-plugin` (run)

---

## 3. Architecture and project structure

```
atlan-mfo-dashboard/
├── pom.xml
├── README.md
├── .gitignore
├── config.properties.example        # template, no secrets
└── src/
    ├── main/
    │   ├── java/com/atlan/mfo/
    │   │   ├── Main.java             # JavaFX entry point (Application)
    │   │   ├── config/
    │   │   │   └── AppConfig.java    # config / env variable reading
    │   │   ├── db/
    │   │   │   ├── Database.java     # HikariCP DataSource, init
    │   │   │   └── Migrations.java   # runs schema.sql + seed-<profile>.sql (dev|prod|none)
    │   │   ├── model/
    │   │   │   ├── FundInvestment.java
    │   │   │   ├── DirectDeal.java
    │   │   │   ├── AppUser.java
    │   │   │   ├── ScoreBreakdown.java
    │   │   │   └── enums/            # Category, DealStatus, BenchmarkStatus, Tier, Role
    │   │   ├── dao/
    │   │   │   ├── FundInvestmentDao.java
    │   │   │   ├── DirectDealDao.java
    │   │   │   ├── UserDao.java
    │   │   │   └── StaleDataException.java   # thrown on an edit conflict (§13.2)
    │   │   ├── scoring/
    │   │   │   ├── ScoringEngine.java      # core: computes ScoreBreakdown
    │   │   │   ├── ScoringProfile.java     # weights and targets of the 3 grids
    │   │   │   ├── GeographyMatcher.java
    │   │   │   ├── TimelineScorer.java
    │   │   │   └── MetricParser.java       # parses "0.10x", "13.7%", etc.
    │   │   ├── auth/
    │   │   │   ├── AuthService.java
    │   │   │   ├── Session.java            # current user + role
    │   │   │   └── PasswordHasher.java
    │   │   ├── ui/
    │   │   │   ├── controllers/            # one controller per screen
    │   │   │   └── util/                   # Formatters, live-scoring bindings
    │   │   └── util/
    │   └── resources/
    │       ├── fxml/                       # login.fxml, change-password.fxml, main.fxml, pipeline.fxml, …
    │       ├── css/atlan-dark.css          # institutional theme
    │       ├── fonts/                       # Inter + Newsreader (TTF bundled)
    │       └── db/
    │           ├── schema.sql
    │           ├── seed-dev.sql            # fictitious startup data (dev)
    │           ├── seed-prod.sql           # admin only, no demo data (production)
    │           └── roles.sql               # application role with minimal privileges (production)
    └── test/
        └── java/com/atlan/mfo/scoring/     # scoring engine tests
```

Layers, bottom to top: `db` → `dao` → `model` + `scoring` → `ui`. The UI never talks directly to the database; it goes through the DAOs. The scoring engine is **pure** (no UI or database dependency) so it can be tested in isolation.

---

## 4. Data model

All financial metrics are stored as numeric (`NUMERIC` / `double`), **nullable**. A `NULL` value means "not reported" and is **excluded** from scoring (see §5), never treated as zero.

### 4.1 Enums

```
Category        : BUYOUT_GROWTH_VC | SECONDARIES | PRIVATE_CREDIT
DealStatus      : INITIAL_REVIEW | SCREENING | DUE_DILIGENCE | IC_VOTE | APPROVED | DECLINED_LOST
BenchmarkStatus : ABOVE_THRESHOLD | BELOW_THRESHOLD | NA
Tier            : STRONG | MODERATE | CAUTION   (derived from the score, not stored in the database)
Role            : ANALYST | PARTNER
```

### 4.2 PostgreSQL schema (`schema.sql`)

```sql
CREATE TYPE category        AS ENUM ('BUYOUT_GROWTH_VC','SECONDARIES','PRIVATE_CREDIT');
CREATE TYPE deal_status     AS ENUM ('INITIAL_REVIEW','SCREENING','DUE_DILIGENCE','IC_VOTE','APPROVED','DECLINED_LOST');
CREATE TYPE benchmark_status AS ENUM ('ABOVE_THRESHOLD','BELOW_THRESHOLD','NA');
CREATE TYPE app_role        AS ENUM ('ANALYST','PARTNER');

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name     TEXT NOT NULL,
    role          app_role NOT NULL DEFAULT 'ANALYST',
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,  -- forced on first login (see §13.3)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Funds: Buyout/growth/VC, Secondaries, Private credit
CREATE TABLE fund_investment (
    id             BIGSERIAL PRIMARY KEY,
    category       category NOT NULL,
    name           TEXT NOT NULL,
    next_steps     TEXT,
    status         deal_status NOT NULL DEFAULT 'INITIAL_REVIEW',
    vs_benchmark   benchmark_status DEFAULT 'NA',
    geography      TEXT,                    -- canonical token: 'US','EUROPE','UK','DACH','GLOBAL','OTHER' (see §13.1)
    asset_class    TEXT,
    commitment     NUMERIC,                 -- capital planned by Atlan (KPI "capital under review", §6.1)

    -- Vintages (full track record, N per fund) live in fund_vintage.

    -- timeline
    first_close    DATE,
    final_close    DATE,

    comments       TEXT,

    -- score snapshot at last save (display = live recalculation)
    score_snapshot INT,
    sub_dpi        NUMERIC,
    sub_irr        NUMERIC,
    sub_moic       NUMERIC,
    sub_geo        NUMERIC,
    sub_time       NUMERIC,

    version        BIGINT NOT NULL DEFAULT 0,   -- optimistic lock (see §13.2)
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     BIGINT REFERENCES app_user(id)
);

-- A fund's vintages: full track record, N per fund (see §5.5)
CREATE TABLE fund_vintage (
    id           BIGSERIAL PRIMARY KEY,
    fund_id      BIGINT NOT NULL REFERENCES fund_investment(id) ON DELETE CASCADE,
    vintage_year INT NOT NULL,
    dpi          NUMERIC,
    tvpi         NUMERIC,
    irr          NUMERIC,                 -- fraction: 0.137 = 13.7%
    moic         NUMERIC,
    UNIQUE (fund_id, vintage_year)
);

-- Direct deals: Co-investment and direct
CREATE TABLE direct_deal (
    id             BIGSERIAL PRIMARY KEY,
    name           TEXT NOT NULL,
    next_steps     TEXT,
    status         deal_status NOT NULL DEFAULT 'INITIAL_REVIEW',
    vs_benchmark   benchmark_status DEFAULT 'NA',
    industry       TEXT,
    gp             TEXT,                    -- general partner / sponsor
    geography      TEXT,
    inv_type       TEXT,                    -- e.g. 'Direct/Growth Equity'
    commitment     NUMERIC,                 -- capital planned by Atlan (KPI "capital under review", §6.1)

    -- financial performance (fractions for percentages)
    revenue        NUMERIC,
    cagr_pct       NUMERIC,                 -- 0.47 = 47%
    ebitda         NUMERIC,
    ebitda_gr_pct  NUMERIC,
    ebitda_mgn_pct NUMERIC,                 -- 0.92 = 92%
    fcf            NUMERIC,
    fcf_conv_pct   NUMERIC,
    ev             NUMERIC,

    -- expected returns
    entry_mult     NUMERIC,
    peers_mult     TEXT,                    -- often a range, e.g. '20-40x'
    exit_val       NUMERIC,
    exp_irr_pct    NUMERIC,
    exp_moic       NUMERIC,

    -- timeline
    deal_deadline  DATE,
    target_exit    DATE,

    comments       TEXT,

    score_snapshot INT,
    sub_cagr       NUMERIC,
    sub_ebitda_mgn NUMERIC,
    sub_fcf        NUMERIC,
    sub_irr        NUMERIC,
    sub_geo        NUMERIC,
    sub_time       NUMERIC,

    version        BIGINT NOT NULL DEFAULT 0,   -- optimistic lock (see §13.2)
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     BIGINT REFERENCES app_user(id)
);
```

**Units convention**: all percentages and IRRs are stored as a **decimal fraction** (`0.137` for 13.7%). Multiples (DPI, MOIC, TVPI) are bare numbers (`1.40` for 1.40x). Display formatting (`x`, `%`, `m`, `bn` suffixes) is handled by a `Formatters` layer on the UI side. Raw input is normalized on entry by `MetricParser`.

---

## 5. Scoring engine (the core of the application)

Scoring is a pure Java module (`com.atlan.mfo.scoring`), with no UI or database dependency. It takes an opportunity (fund or deal) and returns a `ScoreBreakdown`: the sub-scores, the total score, and the tier.

### 5.1 Normalization formula (common to the 3 grids)

```
Earned    = sum of the sub-scores of reported metrics
Possible  = sum of the max points of reported metrics
Score     = MIN( Earned / MAX(Possible, 80) * 100 , 95 )
```

Rules:

- A metric that is **not reported** (`NULL`) is **excluded** from the calculation: it counts in neither `Earned` nor `Possible`. It is never penalized as a zero.
- Each sub-score has a **floor of 0**: a reported but negative metric (e.g. the IRR of a losing fund) is worth 0 points — it counts in `Possible` but never subtracts points from `Earned`.
- The denominator has a **floor of 80**: this prevents sparse data from artificially inflating the score.
- The score is **capped at 95**: no opportunity ever appears "perfect."
- The score is displayed rounded to an integer.

### 5.2 Grid A — Buyout, growth, VC (and Secondaries)

| Component | Max points | Target | Sub-score formula |
|---|---|---|---|
| DPI | 30 | 0.8x | `MIN(DPI / 0.8, 1) * 30` |
| IRR | 25 | 0.30 | `MIN(IRR / 0.3, 1) * 25` |
| MOIC | 20 | 2.5x | `MIN(MOIC / 2.5, 1) * 20` |
| Geography | 15 | US / EU / UK / DACH | match = 15, other = 8, not reported = excluded |
| Timeline | 10 | proximity to final close | ≤30d = 10, ≤60d = 6, ≤90d = 3, otherwise 0 |

### 5.3 Grid B — Private credit

| Component | Max points | Target | Sub-score formula |
|---|---|---|---|
| DPI | 30 | 0.7x | `MIN(DPI / 0.7, 1) * 30` |
| IRR | 25 | 0.20 | `MIN(IRR / 0.2, 1) * 25` |
| MOIC | 20 | 1.8x | `MIN(MOIC / 1.8, 1) * 20` |
| Geography | 15 | US / EU / UK / DACH | match = 15, other = 8, not reported = excluded |
| Timeline | 10 | proximity to final close | ≤30d = 10, ≤60d = 6, ≤90d = 3, otherwise 0 |

### 5.4 Grid C — Co-investment and direct

| Component | Max points | Target | Sub-score formula |
|---|---|---|---|
| Revenue CAGR | 25 | 0.40 | `MIN(CAGR / 0.4, 1) * 25` |
| EBITDA Margin | 20 | 0.35 | `MIN(Margin / 0.35, 1) * 20` |
| FCF Conversion | 10 | 0.90 | `MIN(Conv / 0.9, 1) * 10` |
| Expected IRR | 25 | 0.30 | `MIN(IRR / 0.3, 1) * 25` |
| Geography | 10 | US / EU / UK | match = 10, other = 5, not reported = excluded |
| Timeline | 10 | proximity to deadline | ≤30d = 10, ≤60d = 6, ≤90d = 3, otherwise 0 |

### 5.5 Vintages taken into account for fund scoring

Fund scoring (grids A and B) takes into account **all vintages** of the fund (`fund_vintage` table). For each metric m ∈ {DPI, IRR, MOIC}, a combined value **weighted by recency** of the vintage is computed, before applying the grid's formula:

```
age_v      = most_recent_vintage_year − vintage_year        (0 for the most recent)
weight_v   = 0.5 ^ (age_v / H)                                (H = half-life, default 4 years)
blended_m  = Σ(weight_v × m_v) / Σ(weight_v)   over vintages v where m_v is reported
```

Principle:

- **Recency**: the most recent vintage carries weight 1; a vintage that is H years older carries half the weight, and so on. The half-life `H` is centralized in `ScoringProfile` (adjustable).
- **Intra-fund reference**: age is measured relative to the fund's own most recent vintage (not the calendar year), so weights do not drift over time as long as no vintage is added.
- **Missing data** (consistent with §5.1): a vintage whose metric m is not reported is excluded from the `blended_m` calculation (its weight and value are ignored); if **no** vintage reports m, the metric is excluded from `Earned`/`Possible`.

The `sub_dpi`, `sub_irr`, `sub_moic` columns store the final sub-score (computed on the combined value). In grids A and B (§5.2, §5.3), "DPI / IRR / MOIC" refer to these combined values.

> **Deliberate trade-off**: recency weighting gives weight to young vintages, whose DPI is structurally low (J-curve effect). This is an intentional choice (to reflect the GP's current strategy); increasing the half-life `H` dampens the effect if needed.

### 5.6 Tiers and governance

| Score | Tier | Signal | Action |
|---|---|---|---|
| 70 – 95 | Strong | High confidence | IC vote |
| 40 – 69 | Moderate | Promising, data to complete | Further due diligence |
| 0 – 39 | Caution | Weak or sparse data | Decline / rework |

Governance reminder to display in the app (taken from the methodology): **the score is a decision-support tool; the investment committee retains full authority; a human review is required at every stage.**

### 5.7 Interpretation decisions — confirmed

- **Geography / timeline not provided**: treated as "not reported" and therefore **excluded** from the denominator (consistent with the "missing data excluded" principle), rather than scored as 0. Interpretation **confirmed**. Geography is additionally normalized to a canonical vocabulary to avoid silent non-matches (see §13.1).
- **Timeline and the passage of time**: the timeline sub-score depends on today's date relative to `final_close` / `deal_deadline`. The displayed score is therefore **always recalculated on open** (it can change from one day to the next without the record being edited). The `score_snapshot` in the database is just a snapshot updated on each save, used for fast sorting in lists.
- **Text entries** (e.g. `20-40x`, `20-30%`): excluded from automatic scoring. Input must be decimal (period as separator). `peers_mult` remains a purely informational text field.

### 5.8 Calculation example (grid A, single vintage)

Fund with a **single vintage** (combined values = that vintage's values): DPI = 0.65 / IRR = 0.24 / MOIC = 2.1 / geography = US (match) / no final close provided.

```
sub_dpi  = MIN(0.65/0.8, 1) * 30 = 24.4
sub_irr  = MIN(0.24/0.3, 1) * 25 = 20.0
sub_moic = MIN(2.1/2.5, 1) * 20  = 16.8
sub_geo  = 15  (match)
sub_time = excluded (no date)

Earned   = 24.4 + 20.0 + 16.8 + 15 = 76.2
Possible = 30 + 25 + 20 + 15 = 90
Score    = MIN( 76.2 / MAX(90, 80) * 100, 95 ) = MIN(84.7, 95) = 85  → Strong tier
```

### 5.9 Calculation example (grid A, two vintages, recency weighting)

Fund with two vintages (H = 4 years), geography = US (match), no final close.

| Vintage | Age | Weight `0.5^(age/4)` | DPI | IRR | MOIC |
|---|---|---|---|---|---|
| 2022 (recent) | 0 | 1.000 | 0.30 | 0.26 | 1.9 |
| 2018 | 4 | 0.500 | 1.10 | 0.20 | 2.2 |

Combined values (Σ weights = 1.500):

```
blended_dpi  = (1.000×0.30 + 0.500×1.10) / 1.500 = 0.567
blended_irr  = (1.000×0.26 + 0.500×0.20) / 1.500 = 0.240
blended_moic = (1.000×1.9  + 0.500×2.2 ) / 1.500 = 2.00

sub_dpi  = MIN(0.567/0.8, 1) * 30 = 21.3
sub_irr  = MIN(0.240/0.3, 1) * 25 = 20.0
sub_moic = MIN(2.00/2.5, 1) * 20  = 16.0
sub_geo  = 15  (match)
sub_time = excluded (no date)

Earned   = 21.3 + 20.0 + 16.0 + 15 = 72.3
Possible = 30 + 25 + 20 + 15 = 90
Score    = MIN( 72.3 / MAX(90, 80) * 100, 95 ) = MIN(80.3, 95) = 80  → Strong tier
```

Both examples (§5.8 and §5.9) must appear as-is in the `ScoringEngine` unit tests.

---

## 6. Screens, navigation and behavior

Application shell: fixed **sidebar** on the left, content on the right, top bar with the mode toggle and the current user.

### 6.1 Screens (analyst view)

1. **Pipeline summary** (home) — a KPI band (active deals, capital under review, average score, number in Strong tier) followed by a global table of all opportunities, filterable by strategy and status, with search. Columns: name, strategy, status, score, tier. Sortable by column. Double-click on a row → opens the record. The score shown in the list is **recalculated live** on open, never read from `score_snapshot` (see §13.4).

   KPI definitions: **active deals** = opportunities whose status is neither `APPROVED` nor `DECLINED_LOST`; **capital under review** = sum of the `commitment` of these active opportunities; **average score** and **Strong tier** are computed on this same active subset.
2. **Buyout, growth, VC** — filtered list + fund record.
3. **Secondaries** — same list and same record as Buyout/growth/VC (`category = SECONDARIES`).
4. **Private credit** — same fund structure, scoring grid B.
5. **Co-investment and direct** — list + deal record (operational fields).
6. **Scoring methodology** — **editable** page: the points (weights) and targets of each grid, as well as the global parameters (vintage half-life, floor, cap), are editable and persisted (`scoring_param` table). Saving recalculates all scores. Preferred geographic regions and the timeline day thresholds remain fixed. A button restores the default values.

### 6.2 Data entry / edit record — live scoring

The record (fund or deal) is the direct replacement for Excel data entry. Key behavior: **the score and all sub-scores recalculate on every keystroke.**

Implementation: input fields are bound to JavaFX `Property` objects; a listener on each metric calls `ScoringEngine.score(...)` and updates the display of the score, sub-scores and tier. The calculation is instant and local (no database access). Saving persists the record + the `score_snapshot`, under an **optimistic lock** on `version`: if the record was modified by another user in the meantime, the analyst is warned instead of overwriting it (see §13.2).

The category selector at the top of the fund record (Buyout/growth/VC · Secondaries · Private credit) determines which grid is applied and is reflected live in the score.

### 6.3 The two modes

- **Analyst view**: everything above — dense, editable, filters, data-entry buttons.
- **Presentation mode**: read-only, clean, designed for projection at IC. No sidebar or filters. Content: headline figure (capital under review), 3–4 top-level metrics, allocation by strategy (bars), priority opportunities (top by score), and the governance reminder in the footer. A full-screen mode is desirable.

The toggle is available in the top bar for an analyst. A partner is **locked** into presentation mode (see §7).

---

## 7. Authentication and roles

- Login screen on startup: username + password, checked against `app_user` (BCrypt hash). The current session keeps the user and their role.
- **ANALYST**: full read/write access, lands in the analyst view, can switch to presentation.
- **PARTNER**: read-only, lands in and stays in presentation mode; data-entry screens and edit buttons are not offered.
- No sensitive data or database credentials are stored in the clear in the code. The first administrator user is created by the seed (`seed-dev.sql` in development, `seed-prod.sql` in production).
- **Forced password change**: the admin (and any user provisioned with a temporary password) carries `must_change_password = TRUE`. After a successful authentication, the user is routed to the `change-password.fxml` screen **before** accessing the application, as long as this flag is true (see §13.3).

---

## 8. Design system — institutional theme

Visual direction: "institutional financial terminal." Dominant color **deep petrol `#163B4A`** (background and chrome), **white** text, **bronze `#816430`** accent reserved for actions and the active element. Tabular figures, sharp corners (4px radius), letter-spaced capitalized micro-labels. Austere, dense, distinct from a consumer-facing interface. All colors are centralized in `atlan-dark.css` (named JavaFX colors) so the accent can be changed, or a light theme introduced, from a single place.

### 8.1 Palette

Palette anchored on three colors: `#163B4A` (petrol, dominant), white, `#816430` (bronze, accent). Other values are lighter or darker shades derived from these three colors to give the interface depth.

| Role | Hex |
|---|---|
| Application background (dominant) | `#163B4A` |
| Navigation / bar background | `#11303C` (darkened petrol) |
| Card surface | `#1D4A5B` (lightened petrol) |
| Raised surface / hover / selection | `#245567` |
| Hairline | `rgba(255,255,255,0.08)` |
| Strong hairline | `rgba(255,255,255,0.16)` |
| Primary text | `#FFFFFF` |
| Secondary text | `#AEC3C9` (light petrol gray) |
| Tertiary text / labels | `#79949C` |
| Bronze accent | `#816430` |
| Bronze accent — hover | `#9A7A3C` |
| Bronze accent — wash (button background) | `rgba(129,100,48,0.16)` |
| Strong tier | `#6FA88C` (sage) |
| Moderate tier | `#8FA0B2` (steel) |
| Caution tier | `#C0796A` (terracotta) |

The bronze accent is reserved for interactive elements (buttons, active element border). The large figures in presentation mode stay **white** for a clean look; bronze is used only as an occasional accent.

Tiers use a **colored dot + label**, never a solid pill (sober institutional register). These are functional status indicators, intentionally desaturated and distinct from the brand palette, since they need to encode three risk levels legibly.

### 8.2 Typography

- **Inter** for the entire interface and data, with tabular figures.
- **Newsreader** (serif) reserved for the large figures and headings of presentation mode, for an editorial "boardroom" touch.
- Both fonts are bundled in `resources/fonts/` and loaded via `Font.loadFont` on startup (falling back to system fonts if absent).
- Section micro-labels: capitals, ~0.1em letter-spacing, tertiary gray.

### 8.3 Excerpt of `atlan-dark.css` (named JavaFX colors)

```css
.root {
    -atlan-bg:          #163B4A;   /* petrol, dominant color */
    -atlan-nav:         #11303C;
    -atlan-card:        #1D4A5B;
    -atlan-raised:      #245567;
    -atlan-line:        rgba(255,255,255,0.08);
    -atlan-line-strong: rgba(255,255,255,0.16);
    -atlan-text:        #FFFFFF;
    -atlan-text-2:      #AEC3C9;
    -atlan-text-3:      #79949C;
    -atlan-accent:      #816430;   /* bronze */
    -atlan-accent-hover:#9A7A3C;
    -atlan-strong:      #6FA88C;
    -atlan-moderate:    #8FA0B2;
    -atlan-caution:     #C0796A;

    -fx-background-color: -atlan-bg;
    -fx-font-family: "Inter";
    -fx-text-fill: -atlan-text;
}
```

---

## 9. Configuration and secrets

- Database connection provided via environment variables: `ATLAN_DB_URL`, `ATLAN_DB_USER`, `ATLAN_DB_PASSWORD` (and `ATLAN_DB_SEED` for the seed profile). Failing that, a local `config.properties` file (present locally only, **never committed**).
- Behavior keys: `db.runMigrations` (true in dev, false in production once the schema has been applied) and `db.seed` (`dev` | `prod` | `none`).
- A committed `config.properties.example` file documents the expected keys, without values.
- `.gitignore` excludes `config.properties`, build targets (`target/`), and any secret file.

---

## 10. Development roadmap (milestones)

Each phase corresponds to a coherent batch of work, to be committed and pushed incrementally (see §11).

- **Phase 0 — Foundations**: Maven project, `pom.xml`, HikariCP connection, running `schema.sql` + seed, working login screen + forced password change on first login (§13.3).
- **Phase 1 — Read**: models + DAOs (funds, deals, users), Pipeline summary screen with KPIs and a filterable global table, read-only lists per section.
- **Phase 2 — Scoring**: reworked vintage storage (`fund_vintage`, model + DAO, adapting the record/seed), `ScoringEngine` + `ScoringProfile` (3 grids, recency-weighted multi-vintage aggregation §5.5), `MetricParser`, `GeographyMatcher` (normalization §13.1), `TimelineScorer`, JUnit unit tests covering the 3 grids and edge cases (sparse data, floor of 80, cap of 95, one vs. several vintages, examples §5.8 and §5.9), and switching lists over to live recalculation (§13.4).
- **Phase 3 — Data entry**: fund edit records (3 categories) and direct deal, with live scoring, canonical geography selectors (§13.1), create / edit / save via DAO with optimistic locking (§13.2).
- **Phase 4 — Modes and roles**: presentation mode (full screen), role-based gating (analyst vs. partner), toggle in the top bar.
- **Phase 5 — Polish**: complete `atlan-dark.css` theme, advanced filters and sorting.
- **Phase 6 — Distribution**: `jlink` + `jpackage` packaging (macOS/Windows installers, §13.5), PDF export/print of presentation mode with a light theme — optional.

---

## 11. Git and commit conventions

- **Frequent** and **descriptive** commits: each commit precisely describes what was done, in a way that's readable by an outside person reviewing the history. French messages are accepted; a type prefix is recommended (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- One commit = one logical unit of work. Push regularly, don't accumulate large catch-all commits.
- **Never** add `Co-Authored-By: Claude` or any reference to an author other than the repository owner in commit messages.
- Never commit secrets (database credentials, passwords). Check `.gitignore` before the first push.

---

## 12. Summary of open points — status after review

Implementation details in §13.

1. Fund scoring: **resolved** — **all vintages** (`fund_vintage` table), combined with **recency weighting** (half-life `H`, default 4 years, in `ScoringProfile`). Metric weights unchanged (grid points). (§5.5, §5.9)
2. Empty geography/timeline = **excluded** (confirmed); geography made reliable via canonical vocabulary + normalization. (§5.7, §13.1)
3. Presentation mode: toggle within the same window (confirmed); full-screen PDF export as an optional polish item. (§6.3, §10)
4. **Locked** palette: dominant petrol `#163B4A`, white, bronze accent `#816430`. (§8.1)
5. Theme: dark (petrol) for the app; **light theme reserved for exporting/printing** presentation mode, in Phase 6. (§8, §10)

Newly addressed points: concurrent editing (optimistic lock, §13.2), password change on first login (§13.3), score recalculated live in lists (§13.4), packaging/distribution (§13.5).

---

## 13. Decisions on identified risks

This section locks in the answers to the gaps identified during the spec review. Each point is directly implementable.

### 13.1 Geography — canonical vocabulary and normalization

The `geography` field remains `TEXT` in the database but no longer accepts free-form input: it stores a **canonical token**.

- Canonical set: `US`, `EUROPE`, `UK`, `DACH`, `GLOBAL`, `OTHER`.
- "Preferred" set (full score): grids A/B → `{US, EUROPE, UK, DACH}`; grid C → `{US, EUROPE, UK}`. `GLOBAL` counts as a match (it covers the preferred regions).
- **UI**: geography is chosen from a `ComboBox` (no free-text field), which guarantees a valid token on entry.
- **`GeographyMatcher.normalize(raw)`**: `trim` + uppercasing + an alias table, then returns the canonical token or `null`. Aliases covered at minimum: `USA / U.S. / UNITED STATES → US`; `EU / EUROZONE / EUROPE → EUROPE`; `GB / GREAT BRITAIN / UNITED KINGDOM → UK`; `GERMANY / AUSTRIA / SWITZERLAND / D-A-CH → DACH`; `WORLD / WORLDWIDE / GLOBAL → GLOBAL`. Any unknown non-blank value → `OTHER`.
- **Scoring**: `normalize` first; `null`/blank → **excluded** from the denominator; token ∈ preferred set (or `GLOBAL`) → full score; otherwise (`OTHER` or a region outside the set) → "other" score (8 for A/B, 5 for C).

This removes the risk of a silent non-match ("USA" vs. "US") and makes importing existing data more reliable.

### 13.2 Concurrent editing — optimistic locking

Shared database + several analysts: silent overwrites ("last write wins") are avoided.

- A **`version BIGINT NOT NULL DEFAULT 0`** column added to `fund_investment` and `direct_deal` (see §4.2).
- The model loads `version` along with the record; the DAO's `UPDATE` is conditional:

```sql
UPDATE fund_investment
   SET …, version = version + 1, updated_at = now(), updated_by = ?
 WHERE id = ? AND version = ?;
```

- If `rowsAffected = 0`, the record changed in the meantime → the DAO throws **`StaleDataException`**.
- The UI intercepts it: dialog "This record has been modified by another user since it was opened. Reload the latest data?" → the current version is reloaded; the analyst reapplies their changes. No silent overwrite.

### 13.3 Password change on first login

- **`must_change_password BOOLEAN NOT NULL DEFAULT FALSE`** column in `app_user`; the admin created by the seed is provisioned with `TRUE`.
- After a successful authentication, if `must_change_password = TRUE`, the user is routed to **`change-password.fxml`** (`ChangePasswordController`) **before** accessing the application shell.
- New password: a minimum length is enforced + a confirmation field, hashed with BCrypt, then `must_change_password` is set back to `FALSE`. Same mechanism for any future user provisioned with a temporary password.

### 13.4 Score in lists = live recalculation

Since the engine is pure and cheap to run, it is also called on the list side.

- The **Pipeline summary** and per-section lists recalculate the score and tier **in memory on open**, for each row, using the same `ScoringEngine` as the record view. The displayed score therefore reflects the passage of time (timeline sub-score).
- The `score_snapshot` and `sub_*` columns remain persisted as an **audit trail** of the value at the last save (and for possible offline sorting), but are **never** the display source.
- Consequence: no more list ↔ record divergence. Optional: a discreet indicator if the live score differs from the snapshot (the timeline moved without a re-edit).

### 13.5 Packaging and distribution

The app is a desktop application to be installed on machines (analysts and partners).

- **Runtime image**: `mvn javafx:jlink` produces a reduced JRE embedding the JavaFX modules.
- **Native installer**: `jpackage` generates `.dmg` / `.pkg` (macOS) and `.msi` / `.exe` (Windows) from this image, with `resources/fonts/` fonts included.
- Partners receive the **same** installer; read-only mode is enforced by role gating (§7), not by a separate build.
- Build procedure documented in `README.md`.
