# Déploiement — Atlan MFO Dashboard

Guide pour installer l'application chez un client (multi-family office) : base de
données centralisée, comptes utilisateurs, installeurs par poste, sécurité et
sauvegardes. Décisions transverses : §13 de la spécification.

---

## 1. Vue d'ensemble

```
   Poste analyste ─┐
   Poste partner  ─┼──(réseau, TLS)──▶  PostgreSQL centralisé (une seule base)
   Poste analyste ─┘
```

- **Une seule base** partagée par tous les postes (les données sont communes).
- **L'application** est installée sur chaque poste (installeur autonome, runtime
  Java embarqué : aucun JDK à installer).
- **Multi-utilisateur applicatif** : chacun a son compte dans `app_user`
  (mot de passe BCrypt), avec un rôle `ANALYST` (lecture/écriture) ou `PARTNER`
  (lecture seule, verrouillé en mode présentation).
- L'application se connecte avec **un seul rôle base** (`atlan_app`) à privilèges
  minimaux — jamais un superuser.

---

## 2. Choisir où héberger la base

| Option | Quand la choisir | Exemples |
|---|---|---|
| **PostgreSQL managé (cloud)** | Simplicité : TLS, sauvegardes et mises à jour gérées | Neon, Supabase, AWS RDS, Azure Database for PostgreSQL, DigitalOcean |
| **Serveur on-premise** | Les données doivent rester chez le client | VM / serveur interne, sous leur contrôle |

Dans les deux cas :

- **Ne jamais exposer le port 5432 en clair sur Internet.** Restreindre l'accès
  par VPN, tunnel SSH, ou liste blanche d'IP.
- **TLS obligatoire** côté client (`?sslmode=require` dans l'URL JDBC).

---

## 3. Installation de la base (une seule fois)

Sur le serveur / l'instance managée, avec un compte **administrateur** PostgreSQL :

```bash
# 1. Créer la base
createdb atlan_mfo            # ou via la console du fournisseur managé

# 2. Appliquer le schéma (structure) — depuis une copie du dépôt
psql -d atlan_mfo -f src/main/resources/db/schema.sql

# 3. Créer le rôle applicatif à privilèges minimaux
#    (éditer d'abord db/roles.sql : remplacer CHANGER_CE_MOT_DE_PASSE)
psql -d atlan_mfo -f src/main/resources/db/roles.sql

# 4. Créer le compte administrateur applicatif initial (sans données de démo)
psql -d atlan_mfo -f src/main/resources/db/seed-prod.sql
```

Après cette étape :

- Le rôle base `atlan_app` existe (lecture/écriture uniquement).
- Un compte applicatif `admin` existe avec un mot de passe temporaire
  (`Atlan-Setup-2026`) et l'obligation de le changer au premier login.

> En production, l'application tourne avec `db.runMigrations=false` : le schéma
> n'est pas rejoué au démarrage (il a été appliqué ici, à la main, par le
> propriétaire de la base).

---

## 4. Créer les comptes utilisateurs

Depuis une copie du dépôt, avec un `config.properties` pointant sur la base
centrale, provisionner chaque personne (le mot de passe est temporaire ; l'usager
le change à son premier login) :

```bash
scripts/user-add.sh jdupont "MotDePasseTemporaire1" "Jean Dupont" ANALYST
scripts/user-add.sh mlefevre "MotDePasseTemporaire2" "Marie Lefèvre" PARTNER
```

L'outil hache le mot de passe en BCrypt et insère/rafraîchit le compte. Relancer
la même commande pour **réinitialiser** le mot de passe d'un compte existant.

---

## 5. Installer l'application sur chaque poste

### 5.1 Construire l'installeur (une fois par plateforme cible)

```bash
scripts/package.sh dmg     # macOS  → target/installer/*.dmg
scripts/package.sh msi     # Windows (nécessite WiX Toolset) → *.msi
```

L'installeur embarque un runtime Java réduit : **rien d'autre à installer** sur
le poste.

### 5.2 Signer (recommandé, sinon alertes de sécurité)

- **macOS** : signer avec un certificat *Developer ID Application* puis
  **notariser** auprès d'Apple, sinon Gatekeeper affiche « développeur non
  identifié ».
  ```bash
  export MAC_SIGN_IDENTITY="Developer ID Application: Votre Nom (TEAMID)"
  scripts/package.sh dmg
  # puis notarisation :
  xcrun notarytool submit "target/installer/Atlan MFO Dashboard-1.0.0.dmg" \
      --apple-id "vous@exemple.com" --team-id "TEAMID" --password "<mdp app>" --wait
  xcrun stapler staple "target/installer/Atlan MFO Dashboard-1.0.0.dmg"
  ```
- **Windows** : signer le `.msi` avec `signtool` et un certificat de signature de
  code (Authenticode).

### 5.3 Configurer le poste

Placer un `config.properties` à côté de l'application (ou définir les variables
d'environnement `ATLAN_DB_*`), pointant vers la base centrale, en TLS :

```properties
db.url=jdbc:postgresql://db.client.interne:5432/atlan_mfo?sslmode=require
db.user=atlan_app
db.password=<mot de passe fort du rôle atlan_app>
db.runMigrations=false
db.seed=none
```

### 5.4 Premier login

L'utilisateur ouvre l'application, se connecte avec son identifiant et son mot de
passe temporaire, puis l'application **impose immédiatement** un changement de mot
de passe (§13.3).

---

## 6. Sauvegardes

- **Managé** : activer les sauvegardes automatiques du fournisseur (point-in-time
  recovery si disponible).
- **On-premise** : planifier un `pg_dump` régulier, par exemple quotidien :
  ```bash
  pg_dump -Fc atlan_mfo > /backups/atlan_mfo_$(date +%F).dump
  ```
  et tester régulièrement la restauration (`pg_restore`).

---

## 7. Mises à jour de l'application

1. Incrémenter la version (`pom.xml`, `PKG_VERSION` dans `scripts/package.sh`).
2. Reconstruire et re-signer les installeurs, les redistribuer.
3. Si le **schéma** évolue : appliquer les nouvelles migrations sur la base
   centrale **une fois**, par le propriétaire, avant de déployer la nouvelle
   version aux postes.

---

## 8. Résumé sécurité

- Rôle base `atlan_app` à privilèges minimaux (pas de DDL, pas de superuser).
- TLS obligatoire ; port base non exposé publiquement (VPN / allowlist).
- Mots de passe applicatifs hachés en BCrypt ; changement forcé au 1er login.
- Aucune donnée de démo en production (`db.seed=none`).
- `config.properties` contient des secrets → **jamais versionné** (déjà dans
  `.gitignore`), distribué de façon sécurisée à chaque poste.

> **Limite connue.** L'application parle directement à la base : les identifiants
> du rôle `atlan_app` résident donc sur chaque poste. Pour une petite équipe de
> confiance, sur VPN/TLS, c'est acceptable. Pour une sécurité renforcée, l'étape
> suivante serait d'intercaler une **API backend** entre l'application et la base,
> afin que les secrets et la logique restent côté serveur.

---

## 9. Recommandation d'hébergement (données privées)

« Privé » n'impose pas « on-premise ». Un **PostgreSQL managé bien configuré** est
souvent plus sûr qu'un serveur d'office auto-géré (correctifs, chiffrement et
sauvegardes pris en charge par le fournisseur).

**Recommandé : PostgreSQL managé**, avec région dans la juridiction du client
(résidence des données), chiffrement au repos, TLS, **allowlist d'IP** et
sauvegardes automatiques. Fournisseurs avec allowlist + région EU : Azure Database
for PostgreSQL, AWS RDS, Scaleway, OVHcloud, Neon.

**On-premise** seulement si une contrainte contractuelle/réglementaire impose que
les données ne quittent jamais les locaux.

### Allowlist d'IP — condition de fonctionnement

- **Postes à IP fixe (bureau)** → allowlister l'IP publique du bureau. Simple et robuste.
- **Postes mobiles (IP changeante)** → l'allowlist les bloque : prévoir un **VPN
  d'entreprise à IP de sortie fixe** (on allowliste l'IP du VPN), ou faire passer
  les connexions par le réseau du bureau.

---

## 10. Construire les installeurs Windows

`jpackage` ne produit un paquet que **pour l'OS sur lequel il s'exécute** : on ne
peut pas fabriquer un `.msi` Windows depuis un Mac. Deux options :

1. **CI (recommandé)** — le workflow [`.github/workflows/package.yml`](.github/workflows/package.yml)
   construit automatiquement l'installeur Windows (`.msi`) et macOS (`.dmg`) sur des
   runners GitHub, à chaque tag de version :
   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```
   Les installeurs sont ensuite téléchargeables dans les artefacts du run.

2. **Machine Windows** — installer un JDK 21+ et **WiX Toolset 3.x**, puis :
   ```bash
   scripts/package.sh msi
   ```

### Signature Windows (Authenticode)

Pour éviter l'avertissement SmartScreen, signer le `.msi` avec un certificat de
signature de code :
```
signtool sign /fd SHA256 /a /tr http://timestamp.digicert.com /td SHA256 "Atlan MFO Dashboard-1.0.0.msi"
```
En CI, ajouter le certificat en secret et une étape `signtool` après le build.
