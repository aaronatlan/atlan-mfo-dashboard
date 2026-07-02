# Atlan MFO Dashboard — Spécification technique

> Document de référence pour le développement avec Claude Code.
> Application de suivi de pipeline d'investissement pour un multi-family office (MFO).
> Objectif : remplacer un suivi Excel manuel par une application de bureau structurée, avec scoring automatique et deux modes de consultation.

---

## 1. Objectif et périmètre

Atlan est un multi-family office qui évalue en continu des opportunités d'investissement en marchés privés (fonds et deals directs). Le suivi se fait aujourd'hui à la main sur Excel : saisie répétitive, formules fragiles, pas de contrôle d'accès, pas de rendu propre pour le comité d'investissement (IC).

L'application doit :

- Centraliser toutes les opportunités du pipeline dans une base de données partagée.
- Automatiser le calcul du score de chaque opportunité selon une méthodologie fixe (3 grilles).
- Recalculer le score **en direct** pendant la saisie.
- Offrir deux surfaces distinctes :
  - **Vue analyste** — dense, éditable, surface de travail quotidienne.
  - **Mode présentation** — épuré, en lecture seule, pour projeter en comité d'investissement.
- Gérer plusieurs utilisateurs avec des rôles (analyste / partner).

Le périmètre couvre quatre familles d'opportunités, réparties en deux structures de données :

| Section | Structure de données | Grille de scoring |
|---|---|---|
| Buyout, growth, VC | Fonds | A |
| Secondaries | Fonds (schéma identique à Buyout/growth/VC) | A |
| Private credit | Fonds (schéma identique) | B |
| Co-investissement et direct | Deal (schéma distinct) | C |

---

## 2. Stack technique

Décisions verrouillées :

- **Langage** : Java 21 (LTS).
- **Interface** : JavaFX 21, avec FXML pour la structure des écrans et une feuille de style CSS JavaFX pour le thème.
- **Base de données** : PostgreSQL 15+.
- **Accès données** : JDBC pur via une couche DAO explicite, avec **HikariCP** pour le pool de connexions. Pas de Hibernate/JPA — SQL lisible et transparent, plus simple à auditer par un tiers.
- **Build** : Maven.
- **Hachage des mots de passe** : BCrypt (bibliothèque `at.favre.lib:bcrypt`).
- **Tests** : JUnit 5, avec couverture prioritaire du moteur de scoring.

L'application se connecte à PostgreSQL avec **un seul rôle applicatif** (identifiants dans la configuration, jamais en dur ni versionnés). Le multi-utilisateur est géré **au niveau applicatif** via une table `app_user`, pas via des rôles PostgreSQL par personne. Cela évite de diffuser des identifiants de base sur chaque poste.

### Dépendances Maven principales

- `org.openjfx:javafx-controls:21`
- `org.openjfx:javafx-fxml:21`
- `org.postgresql:postgresql` (driver JDBC)
- `com.zaxxer:HikariCP`
- `at.favre.lib:bcrypt`
- `org.junit.jupiter:junit-jupiter` (test)
- `org.openjfx:javafx-maven-plugin` (exécution)

---

## 3. Architecture et structure du projet

```
atlan-mfo-dashboard/
├── pom.xml
├── README.md
├── .gitignore
├── config.properties.example        # modèle, sans secrets
└── src/
    ├── main/
    │   ├── java/com/atlan/mfo/
    │   │   ├── Main.java             # point d'entrée JavaFX (Application)
    │   │   ├── config/
    │   │   │   └── AppConfig.java    # lecture config / variables d'env
    │   │   ├── db/
    │   │   │   ├── Database.java     # DataSource HikariCP, init
    │   │   │   └── Migrations.java   # exécution schema.sql / seed.sql
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
    │   │   │   └── StaleDataException.java   # levée en cas de conflit d'édition (§13.2)
    │   │   ├── scoring/
    │   │   │   ├── ScoringEngine.java      # cœur : calcule ScoreBreakdown
    │   │   │   ├── ScoringProfile.java     # poids et cibles des 3 grilles
    │   │   │   ├── GeographyMatcher.java
    │   │   │   ├── TimelineScorer.java
    │   │   │   └── MetricParser.java       # parse "0.10x", "13.7%", etc.
    │   │   ├── auth/
    │   │   │   ├── AuthService.java
    │   │   │   ├── Session.java            # utilisateur courant + rôle
    │   │   │   └── PasswordHasher.java
    │   │   ├── ui/
    │   │   │   ├── controllers/            # un contrôleur par écran
    │   │   │   └── util/                   # Formatters, bindings live-scoring
    │   │   └── util/
    │   └── resources/
    │       ├── fxml/                       # login.fxml, change-password.fxml, main.fxml, pipeline.fxml, …
    │       ├── css/atlan-dark.css          # thème institutionnel
    │       ├── fonts/                       # Inter + Newsreader (TTF bundled)
    │       └── db/
    │           ├── schema.sql
    │           └── seed.sql                # données fictives de démarrage
    └── test/
        └── java/com/atlan/mfo/scoring/     # tests du moteur de scoring
```

Couches, du bas vers le haut : `db` → `dao` → `model` + `scoring` → `ui`. L'UI ne parle jamais directement à la base ; elle passe par les DAO. Le moteur de scoring est **pur** (aucune dépendance UI ou base) pour être testable en isolation.

---

## 4. Modèle de données

Toutes les métriques financières sont stockées en numérique (`NUMERIC` / `double`), **nullable**. Une valeur `NULL` signifie « non communiquée » et est **exclue** du scoring (voir §5), jamais traitée comme zéro.

### 4.1 Enums

```
Category        : BUYOUT_GROWTH_VC | SECONDARIES | PRIVATE_CREDIT
DealStatus      : INITIAL_REVIEW | SCREENING | DUE_DILIGENCE | IC_VOTE | APPROVED | DECLINED_LOST
BenchmarkStatus : ABOVE_THRESHOLD | BELOW_THRESHOLD | NA
Tier            : STRONG | MODERATE | CAUTION   (dérivé du score, non stocké en base)
Role            : ANALYST | PARTNER
```

### 4.2 Schéma PostgreSQL (`schema.sql`)

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
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,  -- forcé au 1er login (voir §13.3)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fonds : Buyout/growth/VC, Secondaries, Private credit
CREATE TABLE fund_investment (
    id             BIGSERIAL PRIMARY KEY,
    category       category NOT NULL,
    name           TEXT NOT NULL,
    next_steps     TEXT,
    status         deal_status NOT NULL DEFAULT 'INITIAL_REVIEW',
    vs_benchmark   benchmark_status DEFAULT 'NA',
    geography      TEXT,                    -- token canonique : 'US','EUROPE','UK','DACH','GLOBAL','OTHER' (voir §13.1)
    asset_class    TEXT,
    commitment     NUMERIC,                 -- capital envisagé par Atlan (KPI « capital en revue », §6.1)

    -- millésime le plus récent
    recent_vintage INT,
    recent_dpi     NUMERIC,
    recent_tvpi    NUMERIC,
    recent_irr     NUMERIC,                 -- fraction : 0.137 = 13.7 %
    recent_moic    NUMERIC,

    -- millésime antérieur
    earlier_vintage INT,
    earlier_dpi     NUMERIC,
    earlier_tvpi    NUMERIC,
    earlier_irr     NUMERIC,
    earlier_moic    NUMERIC,

    -- timeline
    first_close    DATE,
    final_close    DATE,

    comments       TEXT,

    -- snapshot du score au dernier enregistrement (affichage = recalcul live)
    score_snapshot INT,
    sub_dpi        NUMERIC,
    sub_irr        NUMERIC,
    sub_moic       NUMERIC,
    sub_geo        NUMERIC,
    sub_time       NUMERIC,

    version        BIGINT NOT NULL DEFAULT 0,   -- verrou optimiste (voir §13.2)
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     BIGINT REFERENCES app_user(id)
);

-- Deals directs : Co-investissement et direct
CREATE TABLE direct_deal (
    id             BIGSERIAL PRIMARY KEY,
    name           TEXT NOT NULL,
    next_steps     TEXT,
    status         deal_status NOT NULL DEFAULT 'INITIAL_REVIEW',
    vs_benchmark   benchmark_status DEFAULT 'NA',
    industry       TEXT,
    gp             TEXT,                    -- general partner / sponsor
    geography      TEXT,
    inv_type       TEXT,                    -- ex. 'Direct/Growth Equity'
    commitment     NUMERIC,                 -- capital envisagé par Atlan (KPI « capital en revue », §6.1)

    -- performance financière (fractions pour les %)
    revenue        NUMERIC,
    cagr_pct       NUMERIC,                 -- 0.47 = 47 %
    ebitda         NUMERIC,
    ebitda_gr_pct  NUMERIC,
    ebitda_mgn_pct NUMERIC,                 -- 0.92 = 92 %
    fcf            NUMERIC,
    fcf_conv_pct   NUMERIC,
    ev             NUMERIC,

    -- retours attendus
    entry_mult     NUMERIC,
    peers_mult     TEXT,                    -- souvent une fourchette, ex. '20-40x'
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

    version        BIGINT NOT NULL DEFAULT 0,   -- verrou optimiste (voir §13.2)
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     BIGINT REFERENCES app_user(id)
);
```

**Convention d'unités** : tous les pourcentages et IRR sont stockés en **fraction décimale** (`0.137` pour 13,7 %). Les multiples (DPI, MOIC, TVPI) sont des nombres nus (`1.40` pour 1,40x). Le formatage d'affichage (suffixes `x`, `%`, `m`, `bn`) est géré par une couche `Formatters` côté UI. La saisie brute est normalisée à l'entrée par `MetricParser`.

---

## 5. Moteur de scoring (cœur de l'application)

Le scoring est un module Java pur (`com.atlan.mfo.scoring`), sans dépendance UI ni base. Il prend une opportunité (fonds ou deal) et retourne un `ScoreBreakdown` : les sous-scores, le score total, et le tier.

### 5.1 Formule de normalisation (commune aux 3 grilles)

```
Earned    = somme des sous-scores des métriques communiquées
Possible  = somme des points max des métriques communiquées
Score     = MIN( Earned / MAX(Possible, 80) * 100 , 95 )
```

Règles :

- Une métrique **non communiquée** (`NULL`) est **exclue** du calcul : elle ne compte ni dans `Earned` ni dans `Possible`. Elle n'est jamais pénalisée comme un zéro.
- Le dénominateur a un **plancher à 80** : cela empêche des données éparses de gonfler artificiellement le score.
- Le score est **plafonné à 95** : aucune opportunité n'apparaît « parfaite ».
- Le score arrondi à l'entier est affiché.

### 5.2 Grille A — Buyout, growth, VC (et Secondaries)

| Composant | Points max | Cible | Formule du sous-score |
|---|---|---|---|
| DPI | 30 | 0,8x | `MIN(DPI / 0.8, 1) * 30` |
| IRR | 25 | 0,30 | `MIN(IRR / 0.3, 1) * 25` |
| MOIC | 20 | 2,5x | `MIN(MOIC / 2.5, 1) * 20` |
| Géographie | 15 | US / EU / UK / DACH | match = 15, autre = 8, non communiquée = exclue |
| Timeline | 10 | proximité du final close | ≤30j = 10, ≤60j = 6, ≤90j = 3, sinon 0 |

### 5.3 Grille B — Private credit

| Composant | Points max | Cible | Formule du sous-score |
|---|---|---|---|
| DPI | 30 | 0,7x | `MIN(DPI / 0.7, 1) * 30` |
| IRR | 25 | 0,20 | `MIN(IRR / 0.2, 1) * 25` |
| MOIC | 20 | 1,8x | `MIN(MOIC / 1.8, 1) * 20` |
| Géographie | 15 | US / EU / UK / DACH | match = 15, autre = 8, non communiquée = exclue |
| Timeline | 10 | proximité du final close | ≤30j = 10, ≤60j = 6, ≤90j = 3, sinon 0 |

### 5.4 Grille C — Co-investissement et direct

| Composant | Points max | Cible | Formule du sous-score |
|---|---|---|---|
| Revenue CAGR | 25 | 0,40 | `MIN(CAGR / 0.4, 1) * 25` |
| EBITDA Margin | 20 | 0,35 | `MIN(Margin / 0.35, 1) * 20` |
| FCF Conversion | 10 | 0,90 | `MIN(Conv / 0.9, 1) * 10` |
| Expected IRR | 25 | 0,30 | `MIN(IRR / 0.3, 1) * 25` |
| Géographie | 10 | US / EU / UK | match = 10, autre = 5, non communiquée = exclue |
| Timeline | 10 | proximité du deadline | ≤30j = 10, ≤60j = 6, ≤90j = 3, sinon 0 |

### 5.5 Millésimes pris en compte pour le scoring des fonds

Le scoring des fonds (grilles A et B) s'appuie sur les métriques du **millésime le plus récent** (`recent_dpi`, `recent_irr`, `recent_moic`). Le millésime antérieur est stocké et affiché à titre de contexte mais **n'entre pas** dans le score. Décision retenue : simplicité et défendabilité devant l'IC, sans interprétation discutable d'une pondération.

### 5.6 Tiers et gouvernance

| Score | Tier | Signal | Action |
|---|---|---|---|
| 70 – 95 | Strong | Confiance élevée | Vote IC |
| 40 – 69 | Moderate | Prometteur, données à compléter | DD approfondie |
| 0 – 39 | Caution | Faible ou données éparses | Décliner / retravailler |

Rappel de gouvernance à afficher dans l'app (repris de la méthodologie) : **le score est un support de décision ; le comité d'investissement conserve l'entière autorité ; une revue humaine est requise à tous les niveaux.**

### 5.7 Décisions d'interprétation à confirmer

- **Géographie / timeline non renseignées** : traitées comme « non communiquées » donc **exclues** du dénominateur (cohérent avec le principe « missing data excluded »), plutôt que scorées à 0. Interprétation **retenue**. La géographie est en outre normalisée vers un vocabulaire canonique pour éviter les non-match silencieux (voir §13.1).
- **Timeline et écoulement du temps** : le sous-score timeline dépend de la date du jour par rapport au `final_close` / `deal_deadline`. Le score affiché est donc **toujours recalculé à l'ouverture** (il peut évoluer d'un jour à l'autre sans modification de la fiche). Le `score_snapshot` en base n'est qu'une photo mise à jour à chaque enregistrement, utilisée pour le tri rapide dans les listes.
- **Entrées textuelles** (ex. `20-40x`, `20-30%`) : exclues du scoring automatique. La saisie doit se faire en décimale (point milieu). `peers_mult` reste un champ texte purement informatif.

### 5.8 Exemple de calcul (grille A)

Fonds avec DPI = 0,65 / IRR = 0,24 / MOIC = 2,1 / géographie = US (match) / pas de final close renseigné.

```
sub_dpi  = MIN(0.65/0.8, 1) * 30 = 24.4
sub_irr  = MIN(0.24/0.3, 1) * 25 = 20.0
sub_moic = MIN(2.1/2.5, 1) * 20  = 16.8
sub_geo  = 15  (match)
sub_time = exclu (pas de date)

Earned   = 24.4 + 20.0 + 16.8 + 15 = 76.2
Possible = 30 + 25 + 20 + 15 = 90
Score    = MIN( 76.2 / MAX(90, 80) * 100, 95 ) = MIN(84.7, 95) = 85  → tier Strong
```

Ce cas doit figurer tel quel dans les tests unitaires du `ScoringEngine`.

---

## 6. Écrans, navigation et comportements

Coquille applicative : **menu latéral** fixe à gauche, contenu à droite, barre supérieure avec la bascule de mode et l'utilisateur courant.

### 6.1 Écrans (vue analyste)

1. **Pipeline summary** (accueil) — bande de KPI (deals actifs, capital en revue, score moyen, nombre en tier Strong) puis tableau global de toutes les opportunités, filtrable par stratégie et statut, avec recherche. Colonnes : nom, stratégie, statut, score, tier. Tri par colonne. Double-clic sur une ligne → ouvre la fiche. Le score affiché en liste est **recalculé en direct** à l'ouverture, jamais lu depuis `score_snapshot` (voir §13.4). *(Note de phasage : tant que le moteur de scoring (Phase 2) n'est pas branché, la Phase 1 affiche `score_snapshot` ; le recalcul live remplace cette source en Phase 2.)*

   Définition des KPI : **deals actifs** = opportunités dont le statut n'est ni `APPROVED` ni `DECLINED_LOST` ; **capital en revue** = somme des `commitment` de ces opportunités actives ; **score moyen** et **tier Strong** sont calculés sur ce même sous-ensemble actif.
2. **Buyout, growth, VC** — liste filtrée + fiche fonds.
3. **Secondaries** — même liste et même fiche que Buyout/growth/VC (`category = SECONDARIES`).
4. **Private credit** — même structure fonds, grille de scoring B.
5. **Co-investissement et direct** — liste + fiche deal (champs opérationnels).
6. **Scoring methodology** — page de référence en lecture seule affichant les 3 grilles, la formule de normalisation et les tiers. Sert de documentation intégrée.

### 6.2 Fiche de saisie / édition — scoring en direct

La fiche (fonds ou deal) est le remplacement direct de la saisie Excel. Comportement clé : **le score et tous les sous-scores se recalculent à chaque frappe.**

Implémentation : les champs de saisie sont liés à des `Property` JavaFX ; un listener sur chaque métrique appelle `ScoringEngine.score(...)` et met à jour l'affichage du score, des sous-scores et du tier. Le calcul est instantané et local (pas d'accès base). L'enregistrement persiste la fiche + le `score_snapshot`, sous **verrou optimiste** sur `version` : si la fiche a été modifiée par un autre utilisateur entre-temps, l'analyste est prévenu au lieu d'écraser (voir §13.2).

Le sélecteur de catégorie en haut de la fiche fonds (Buyout/growth/VC · Secondaries · Private credit) détermine la grille appliquée et se reflète en direct dans le score.

### 6.3 Les deux modes

- **Vue analyste** : tout ce qui précède — dense, éditable, filtres, boutons de saisie.
- **Mode présentation** : lecture seule, épuré, pensé pour la projection en IC. Pas de menu latéral ni de filtres. Contenu : chiffre-phare (capital en revue), 3–4 métriques de tête, allocation par stratégie (barres), opportunités prioritaires (top par score), et le rappel de gouvernance en pied. Un mode plein écran est souhaitable.

La bascule se fait via le toggle en barre supérieure pour un analyste. Un partner est **verrouillé** en mode présentation (voir §7).

---

## 7. Authentification et rôles

- Écran de login au démarrage : identifiant + mot de passe, vérifiés contre `app_user` (hash BCrypt). La session en cours conserve l'utilisateur et son rôle.
- **ANALYST** : accès complet lecture/écriture, atterrit en vue analyste, peut basculer en présentation.
- **PARTNER** : lecture seule, atterrit et reste en mode présentation ; les écrans de saisie et boutons d'édition ne lui sont pas proposés.
- Aucune donnée sensible ni identifiant de base n'est stockée en clair dans le code. Le premier utilisateur administrateur est créé par le script `seed.sql`.
- **Changement de mot de passe forcé** : l'admin (et tout utilisateur provisionné avec un mot de passe temporaire) porte `must_change_password = TRUE`. Après une authentification réussie, l'utilisateur est routé vers l'écran `change-password.fxml` **avant** d'accéder à l'application, tant que ce drapeau est vrai (voir §13.3).

---

## 8. Design system — thème institutionnel

Direction visuelle : « terminal financier institutionnel ». Couleur dominante **pétrole profond `#163B4A`** (fond et chrome), texte **blanc**, accent **bronze `#816430`** réservé aux actions et à l'élément actif. Chiffres tabulaires, angles nets (rayon 4 px), micro-labels en capitales espacées. Sobre, dense, distinct d'une interface grand public. Toutes les couleurs sont centralisées dans `atlan-dark.css` (couleurs nommées JavaFX) pour pouvoir changer d'accent ou passer en thème clair d'un seul endroit.

### 8.1 Palette

Palette ancrée sur trois couleurs : `#163B4A` (pétrole, dominante), blanc, `#816430` (bronze, accent). Les autres valeurs sont des nuances plus claires ou plus sombres dérivées de ces trois couleurs pour donner de la profondeur à l'interface.

| Rôle | Hex |
|---|---|
| Fond application (dominante) | `#163B4A` |
| Fond navigation / barre | `#11303C` (pétrole assombri) |
| Surface carte | `#1D4A5B` (pétrole éclairci) |
| Surface surélevée / hover / sélection | `#245567` |
| Filet (hairline) | `rgba(255,255,255,0.08)` |
| Filet fort | `rgba(255,255,255,0.16)` |
| Texte principal | `#FFFFFF` |
| Texte secondaire | `#AEC3C9` (gris pétrolé clair) |
| Texte tertiaire / labels | `#79949C` |
| Accent bronze | `#816430` |
| Accent bronze — hover | `#9A7A3C` |
| Accent bronze — wash (fond bouton) | `rgba(129,100,48,0.16)` |
| Tier Strong | `#6FA88C` (sauge) |
| Tier Moderate | `#8FA0B2` (acier) |
| Tier Caution | `#C0796A` (terre cuite) |

L'accent bronze est réservé aux éléments interactifs (boutons, bordure de l'élément actif). Les grands chiffres du mode présentation restent en **blanc** pour un rendu épuré ; le bronze ne sert que d'accent ponctuel.

Les tiers utilisent un **point coloré + libellé**, jamais une pastille pleine (registre institutionnel sobre). Ce sont des indicateurs de statut fonctionnels, volontairement désaturés et distincts de la palette de marque, car ils doivent encoder trois niveaux de risque de façon lisible.

### 8.2 Typographie

- **Inter** pour l'ensemble de l'interface et les données, avec chiffres tabulaires.
- **Newsreader** (serif) réservé aux grands chiffres et titres du mode présentation, pour une touche éditoriale « salle de conseil ».
- Les deux polices sont embarquées dans `resources/fonts/` et chargées via `Font.loadFont` au démarrage (fallback système si absentes).
- Micro-labels de section : capitales, interlettrage ~0,1em, gris tertiaire.

### 8.3 Extrait `atlan-dark.css` (couleurs nommées JavaFX)

```css
.root {
    -atlan-bg:          #163B4A;   /* pétrole, couleur dominante */
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

## 9. Configuration et secrets

- Connexion base fournie par variables d'environnement : `ATLAN_DB_URL`, `ATLAN_DB_USER`, `ATLAN_DB_PASSWORD`. À défaut, un fichier local `config.properties` (présent seulement en local, **jamais versionné**).
- Un fichier `config.properties.example` versionné documente les clés attendues, sans valeurs.
- `.gitignore` exclut `config.properties`, les cibles de build (`target/`), et tout fichier de secret.

---

## 10. Roadmap de développement (jalons)

Chaque phase correspond à un lot cohérent, à committer et pousser au fil de l'eau (voir §11).

- **Phase 0 — Fondations** : projet Maven, `pom.xml`, connexion HikariCP, exécution de `schema.sql` + `seed.sql`, écran de login fonctionnel + changement de mot de passe forcé au 1er login (§13.3).
- **Phase 1 — Lecture** : modèles + DAO (fonds, deals, users), écran Pipeline summary avec KPI et tableau global filtrable, listes par section en lecture.
- **Phase 2 — Scoring** : `ScoringEngine` + `ScoringProfile` (3 grilles, scoring sur le millésime récent §5.5), `MetricParser`, `GeographyMatcher` (normalisation §13.1), `TimelineScorer`, tests unitaires JUnit couvrant les 3 grilles et les cas limites (données éparses, plancher 80, plafond 95, exemple §5.8).
- **Phase 3 — Saisie** : fiches d'édition fonds (3 catégories) et deal direct, avec scoring en direct, sélecteurs de géographie canoniques (§13.1), création / modification / enregistrement via DAO avec verrou optimiste (§13.2).
- **Phase 4 — Modes et rôles** : mode présentation (plein écran), gating par rôle (analyste vs partner), bascule en barre supérieure.
- **Phase 5 — Finitions** : thème complet `atlan-dark.css`, filtres et tri avancés.
- **Phase 6 — Distribution** : packaging `jlink` + `jpackage` (installeurs macOS/Windows, §13.5), export/impression PDF du mode présentation avec thème clair — optionnel.

---

## 11. Conventions Git et de commit

- Commits **fréquents** et **descriptifs** : chaque commit décrit précisément ce qui a été fait, de façon lisible pour une personne extérieure qui analyse l'historique. Message en français accepté ; préfixe de type recommandé (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- Un commit = une unité logique de travail. Pousser régulièrement, ne pas accumuler de gros commits fourre-tout.
- **Ne jamais** ajouter `Co-Authored-By: Claude` ni aucune référence à un auteur autre que le propriétaire du dépôt dans les messages de commit.
- Ne jamais committer de secrets (identifiants base, mots de passe). Vérifier le `.gitignore` avant le premier push.

---

## 12. Récapitulatif des points — statut après revue

Détails d'implémentation en §13.

1. Scoring des fonds : **résolu** — sur le seul **millésime récent** ; le millésime antérieur reste du contexte non scoré. (§5.5)
2. Géographie/timeline vides = **exclues** (confirmé) ; géographie fiabilisée par vocabulaire canonique + normalisation. (§5.7, §13.1)
3. Mode présentation : bascule dans la même fenêtre (confirmé) ; export PDF plein écran en finition optionnelle. (§6.3, §10)
4. Palette **verrouillée** : pétrole `#163B4A` dominant, blanc, accent bronze `#816430`. (§8.1)
5. Thème : sombre (pétrole) pour l'app ; **thème clair réservé à l'export/impression** du mode présentation, en Phase 6. (§8, §10)

Points nouvellement traités : édition concurrente (verrou optimiste, §13.2), changement de mot de passe au 1er login (§13.3), score recalculé en direct dans les listes (§13.4), packaging/distribution (§13.5).

---

## 13. Décisions sur les risques identifiés

Cette section fige les réponses aux manques repérés lors de la revue de la spec. Chaque point est directement implémentable.

### 13.1 Géographie — vocabulaire canonique et normalisation

Le champ `geography` reste `TEXT` en base mais n'accepte plus de saisie libre : il stocke un **token canonique**.

- Ensemble canonique : `US`, `EUROPE`, `UK`, `DACH`, `GLOBAL`, `OTHER`.
- Set « préféré » (plein score) : grilles A/B → `{US, EUROPE, UK, DACH}` ; grille C → `{US, EUROPE, UK}`. `GLOBAL` compte comme un match (il couvre les régions préférées).
- **UI** : la géographie est choisie dans un `ComboBox` (pas de champ texte libre), ce qui garantit un token valide à la saisie.
- **`GeographyMatcher.normalize(raw)`** : `trim` + passage en majuscules + table d'alias, puis renvoie le token canonique ou `null`. Alias couverts a minima : `USA / U.S. / UNITED STATES / ÉTATS-UNIS → US` ; `EU / EUROZONE / EUROPE → EUROPE` ; `GB / GREAT BRITAIN / UNITED KINGDOM → UK` ; `GERMANY / AUSTRIA / SWITZERLAND / D-A-CH → DACH` ; `WORLD / WORLDWIDE / GLOBAL → GLOBAL`. Toute valeur inconnue non vide → `OTHER`.
- **Scoring** : `normalize` d'abord ; `null`/vide → **exclu** du dénominateur ; token ∈ set préféré (ou `GLOBAL`) → plein score ; sinon (`OTHER` ou région hors set) → score « autre » (8 pour A/B, 5 pour C).

Cela supprime le risque de non-match silencieux (« USA » vs « US ») et fiabilise l'import de données existantes.

### 13.2 Édition concurrente — verrou optimiste

Base partagée + plusieurs analystes : on évite l'écrasement silencieux (« last write wins »).

- Colonne **`version BIGINT NOT NULL DEFAULT 0`** ajoutée à `fund_investment` et `direct_deal` (voir §4.2).
- Le modèle charge `version` avec la fiche ; l'`UPDATE` du DAO est conditionnel :

```sql
UPDATE fund_investment
   SET …, version = version + 1, updated_at = now(), updated_by = ?
 WHERE id = ? AND version = ?;
```

- Si `rowsAffected = 0`, la fiche a changé entre-temps → le DAO lève **`StaleDataException`**.
- L'UI intercepte : dialogue « Cette fiche a été modifiée par un autre utilisateur depuis son ouverture. Recharger les données à jour ? » → rechargement de la version courante ; l'analyste réapplique ses modifications. Aucun écrasement muet.

### 13.3 Changement de mot de passe au premier login

- Colonne **`must_change_password BOOLEAN NOT NULL DEFAULT FALSE`** dans `app_user` ; l'admin créé par `seed.sql` est provisionné à `TRUE`.
- Après une authentification réussie, si `must_change_password = TRUE`, l'utilisateur est routé vers **`change-password.fxml`** (`ChangePasswordController`) **avant** d'accéder à la coquille applicative.
- Nouveau mot de passe : longueur minimale imposée + champ de confirmation, haché en BCrypt, puis `must_change_password` repassé à `FALSE`. Même mécanisme pour tout futur utilisateur provisionné avec un mot de passe temporaire.

### 13.4 Score en liste = recalcul en direct

Le moteur étant pur et peu coûteux, on l'appelle aussi côté listes.

- Le **Pipeline summary** et les listes par section recalculent le score et le tier **en mémoire à l'ouverture**, pour chaque ligne, avec le même `ScoringEngine` que la fiche. Le score affiché tient donc compte de l'écoulement du temps (sous-score timeline).
- Les colonnes `score_snapshot` et `sub_*` restent persistées comme **trace d'audit** de la valeur au dernier enregistrement (et tri hors-ligne éventuel), mais ne sont **jamais** la source d'affichage.
- Conséquence : plus de divergence liste ↔ fiche. Optionnel : un indicateur discret si le score live diffère du snapshot (la timeline a bougé sans réédition).

### 13.5 Packaging et distribution

L'app est une application de bureau à installer sur les postes (analystes et partners).

- **Image runtime** : `mvn javafx:jlink` produit un JRE réduit embarquant les modules JavaFX.
- **Installeur natif** : `jpackage` génère `.dmg` / `.pkg` (macOS) et `.msi` / `.exe` (Windows) à partir de cette image, polices `resources/fonts/` incluses.
- Les partners reçoivent le **même** installeur ; le mode lecture seule est assuré par le gating de rôle (§7), pas par un build séparé.
- Procédure de build documentée dans le `README.md`.
