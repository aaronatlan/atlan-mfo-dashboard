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
`src/main/resources/db/schema.sql` puis `seed.sql` (idempotents).

## Lancer

```bash
mvn clean javafx:run
```

## Premier login

Le seed crée un compte administrateur :

- identifiant : `admin`
- mot de passe : `admin`

Ce compte est marqué `must_change_password` : l'application impose un changement
de mot de passe avant d'accéder aux écrans (voir §13.3 de la spec).

## Tests

```bash
mvn test
```

## État d'avancement

- **Phase 0 — Fondations** *(en cours)* : projet Maven, connexion HikariCP,
  migrations, login + changement de mot de passe forcé.

Roadmap détaillée : §10 de la spec.
