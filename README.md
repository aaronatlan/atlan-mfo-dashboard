# Atlan MFO Dashboard

Application de bureau (JavaFX) de suivi du pipeline d'investissement d'un multi-family office.
Voir la spécification complète : [`atlan-mfo-dashboard-spec.md`](atlan-mfo-dashboard-spec.md).

## Prérequis

- JDK 21+ (testé sous JDK 25, compilation ciblée Java 21)
- Maven 3.9+
- PostgreSQL 15+

## Configuration

La connexion est fournie soit par variables d'environnement, soit par un fichier
local `config.properties` (jamais versionné) :

```bash
cp config.properties.example config.properties
# puis renseigner db.url / db.user / db.password
```

Variables d'environnement équivalentes (prioritaires) :
`ATLAN_DB_URL`, `ATLAN_DB_USER`, `ATLAN_DB_PASSWORD`.

## Base de données

Créer la base une fois :

```bash
createdb atlan_mfo
```

Au démarrage, si `db.runMigrations=true`, l'application exécute
`src/main/resources/db/schema.sql` puis le seed du profil choisi via `db.seed`
(`dev` = données de démonstration, `prod` = admin seul, `none` = aucun). Tous
les scripts sont idempotents.

## Lancer

```bash
mvn clean javafx:run
```

## Premier login (seed de développement, `db.seed=dev`)

Le seed **dev** crée deux comptes de démonstration :

- `admin` / `admin` — analyste ; `must_change_password` : l'application impose
  un changement de mot de passe avant d'accéder aux écrans (voir §13.3 de la spec) ;
- `partner` / `partner` — lecture seule, verrouillé en mode présentation (§6.3, §7).

En **production** (`db.seed=prod`), seul un compte `admin` à mot de passe
temporaire est créé ; les autres utilisateurs sont provisionnés via
`scripts/user-add.sh` (voir [DEPLOYMENT.md](DEPLOYMENT.md)).

## Tests

```bash
mvn test
```

## Packaging (distribution)

Génère un paquet natif autonome (runtime Java embarqué via `jlink`/`jpackage` ;
aucun JDK requis sur le poste cible), voir §13.5 :

```bash
scripts/package.sh app-image     # bundle portable (.app / dossier)
scripts/package.sh dmg           # installeur macOS
scripts/package.sh msi           # installeur Windows (nécessite WiX)
```

Le résultat est écrit dans `target/installer/`.

> **macOS + iCloud** : si le dépôt est dans un dossier synchronisé iCloud
> (Bureau, Documents), la signature ad-hoc de `jpackage` échoue à cause de
> l'attribut étendu `com.apple.FinderInfo`. Cloner/construire depuis un dossier
> hors iCloud (ex. `~/dev/`) résout le problème.

### Polices

Le thème utilise **Inter** et **Newsreader**. Elles sont chargées depuis
`src/main/resources/fonts/` si présentes (`Inter-Regular.ttf`,
`Inter-SemiBold.ttf`, `Newsreader-Regular.ttf`), sinon fallback système propre.
Déposer les TTF statiques dans ce dossier suffit à les embarquer.

## État d'avancement

- **Phase 0 — Fondations**, **1 — Lecture**, **2 — Scoring**, **3 — Saisie**,
  **4 — Modes & rôles**, **5 — Finitions**, **6 — Distribution** : livrées.

Roadmap détaillée : §10 de la spec ; décisions transverses : §13.
