# Backlog V1 — User stories par itération

Ce backlog découpe la V1 en 33 itérations, regroupées en 10 blocs (A à J). Il complète la section « Plan de mise en place » de [CLAUDE.md](../CLAUDE.md).

## Personas

| Persona | Rôle |
|---|---|
| **Utilisateur** | dépose, consulte et recherche des documents |
| **Administrateur fonctionnel** | configure classes, champs, référentiels, workflows, droits |
| **Valideur** | traite les tâches de workflow |
| **Responsable conformité** | gère la rétention, les legal holds et l'audit |
| **Intégrateur** | connecte une application tierce (ERP, scanner) par l'API |
| **Exploitant** | déploie et surveille la GED sur OpenShift |
| **Développeur** | construit et maintient la GED |

## Définition de « terminé » (commune à toutes les stories)

- Les critères d'acceptation sont vérifiés par des **tests automatisés** : unitaires, ou intégration avec Testcontainers.
- `mvn verify` passe, et la démo de l'itération fonctionne.
- Le contrat `ged-v1.yaml` est à jour si l'API change.
- Toute action métier produit un événement d'audit (à partir de l'itération 10).
- Le code n'appelle **rien en dehors de l'entreprise**, et le frontend n'utilise aucun CDN.
- Le code a été expliqué au product owner, qui a validé l'itération.

Tailles : **S** (moins d'une journée), **M** (1 à 2 jours), **L** (3 à 5 jours).

---

## A. Fondations

### Itération 1 : Hello GED

**US-01 — Vérifier que la GED répond** · S
> En tant qu'**exploitant**, je veux un endpoint de santé et un endpoint `ping` afin de vérifier que l'application est démarrée.

Critères d'acceptation :
- `GET /api/v1/ping` renvoie `200` avec `{"status":"ok","version":"<version>"}`.
- `GET /actuator/health` renvoie `UP`.
- Un test d'intégration Spring Boot couvre `/ping`.

Notes techniques : parent Maven + module `ged-api`, Java 25, Spring Boot 4.1.

Tâches :
- [x] T01.1 Créer le `pom.xml` parent : Java 25, Spring Boot 4.1.1 (parent), groupId `ch.louhan.ged` (Maven Central en dev ; miroir interne pour la CI, plus tard)
- [x] T01.2 Créer le module `ged-api` : classe `GedApplication`, `application.yml`
- [x] T01.3 Écrire `PingController` → `GET /api/v1/ping`, avec la version lue depuis `build-info`
- [x] T01.4 Activer Actuator (`health`, `info`) et n'exposer que ces endpoints
- [x] T01.5 Écrire le test d'intégration `PingControllerIT` (`@SpringBootTest` + `MockMvc`)
- [x] T01.6 Ajouter `.gitignore`, `README.md` et le wrapper Maven (`mvnw`)

### Itération 2 : Dossiers

**US-02 — Créer et lister des dossiers** · M
> En tant qu'**utilisateur**, je veux créer des dossiers et des sous-dossiers afin d'organiser mes documents.

Critères d'acceptation :
- `POST /api/v1/folders` crée un dossier (nom, parent facultatif) et renvoie `201` avec son id.
- `GET /api/v1/folders/{id}/children` liste les sous-dossiers, avec pagination par curseur.
- Deux dossiers frères ne peuvent pas porter le même nom : sinon, `409`.
- Renommer et supprimer un dossier vide fonctionnent. Supprimer un dossier non vide renvoie `409`.

Notes techniques : PostgreSQL 16, Flyway `V1__folders.sql`, Spring Data JPA, Testcontainers, `docker-compose.yml` (PostgreSQL seul).

Tâches :
- [x] T02.1 Créer `deploy/docker-compose.yml` avec PostgreSQL 18
- [x] T02.2 Ajouter Flyway et écrire `V1__folders.sql` : table `folder` (id UUID, name, parent_id, created_at) et contrainte unique `NULLS NOT DISTINCT` (parent_id, name) — `created_by` ajouté à l'itération 4 (authentification)
- [x] T02.3 Écrire l'entité JPA `Folder` et le `FolderRepository`
- [x] T02.4 Écrire `FolderService` : créer, renommer, supprimer (refuser si non vide), lister les enfants
- [x] T02.5 Implémenter la pagination par curseur (keyset sur `name` seul, unique parmi les enfants d'un même parent), avec encodage et décodage du curseur
- [x] T02.6 Écrire `FolderController` (CRUD + `/children`)
- [x] T02.7 Créer la base de test commune `AbstractIntegrationTest` (Testcontainers PostgreSQL)
- [x] T02.8 Écrire les tests : création, doublon → 409, suppression d'un dossier non vide → 409, pagination

### Itération 3 : Contrat d'abord

**US-03 — Disposer d'un contrat d'API documenté** · M
> En tant qu'**intégrateur**, je veux une documentation OpenAPI à jour et consultable afin de développer mon intégration sans lire le code.

Critères d'acceptation :
- `ged-api/src/main/resources/openapi/ged-v1.yaml` décrit `/ping` et `/folders`.
- Les interfaces Spring sont **générées** par `openapi-generator`, et les controllers les implémentent.
- Les erreurs suivent la RFC 9457 (`application/problem+json`).
- Swagger UI est servi localement sur `/api/docs`, sans CDN.
- Le lint du contrat (Spectral) passe dans la CI.

Tâches :
- [ ] T03.1 Écrire `ged-v1.yaml` : `info`, `servers`, `/ping`, `/folders`, schémas `Folder` et `Page`
- [ ] T03.2 Définir le schéma `Problem` (RFC 9457) et les réponses d'erreur communes
- [ ] T03.3 Configurer `openapi-generator-maven-plugin` (interfaces Spring uniquement, `interfaceOnly`, DTO générés)
- [ ] T03.4 Faire implémenter l'interface générée par `FolderController`, et mapper entités ↔ DTO (MapStruct)
- [ ] T03.5 Écrire le `@RestControllerAdvice` qui transforme les exceptions métier en `ProblemDetail`
- [ ] T03.6 Servir Swagger UI et le YAML en local sur `/api/docs` (webjar, sans CDN)
- [ ] T03.7 Ajouter le lint Spectral du contrat (règles + script CI)
- [ ] T03.8 Écrire un test de contrat qui vérifie que les réponses respectent le schéma (validation OpenAPI dans les tests)

### Itération 4 : Sécurité

**US-04 — Se connecter avec son compte d'entreprise** · M
> En tant qu'**utilisateur**, je veux m'authentifier avec mon compte d'entreprise (SSO) afin de ne pas gérer un mot de passe de plus.

Critères d'acceptation :
- Sans jeton, toute route `/api/v1/**` renvoie `401`, sauf `/ping`.
- Un jeton Keycloak valide donne accès. Les groupes Keycloak sont lus depuis le jeton.
- Un rôle `ged-admin` est requis pour les routes d'administration : sans lui, `403`.
- Le realm de dev `deploy/keycloak/realm-ged.json` contient des utilisateurs et des groupes de test.

Tâches :
- [ ] T04.1 Ajouter Keycloak au docker-compose, avec import de `deploy/keycloak/realm-ged.json`
- [ ] T04.2 Créer le realm de dev : client `ged-web` (public, PKCE), utilisateurs `alice`/`bob`/`admin`, groupes, rôle `ged-admin`
- [ ] T04.3 Configurer `ged-api` en resource server JWT (`spring-boot-starter-oauth2-resource-server`)
- [ ] T04.4 Écrire le convertisseur de JWT : rôles et groupes Keycloak → `GrantedAuthority`
- [ ] T04.5 Écrire `SecurityConfig` : `/ping` et `/actuator/health` publics, le reste authentifié, `/admin/**` réservé à `ged-admin`
- [ ] T04.6 Déclarer le security scheme OIDC dans `ged-v1.yaml`
- [ ] T04.7 Écrire `CurrentUser`, qui donne l'utilisateur et ses groupes courants
- [ ] T04.8 Écrire les tests : sans jeton → 401, sans rôle → 403, avec jeton → 200 (Testcontainers Keycloak ou JWT simulé)

**US-05 — Connecter une application par compte de service** · S
> En tant qu'**intégrateur**, je veux un compte de service avec des droits limités afin que mon application appelle la GED sans compte humain.

Critères d'acceptation :
- Un client Keycloak *client credentials* obtient un jeton.
- Les scopes (`documents:read`, `documents:write`…) limitent les routes accessibles.

Tâches :
- [ ] T05.1 Créer le client Keycloak `ged-test-integration` (*client credentials*) dans le realm
- [ ] T05.2 Définir les scopes `documents:read`, `documents:write`, `schema:admin`, `workflow:act` comme *client scopes*
- [ ] T05.3 Mapper les scopes en autorités `SCOPE_*` et protéger les routes en conséquence
- [ ] T05.4 Déclarer le flux `clientCredentials` dans `ged-v1.yaml`
- [ ] T05.5 Écrire le test : jeton de service avec `documents:read` → accès en lecture, écriture refusée
- [ ] T05.6 Écrire le script `docs/exemples/token-service.sh` (curl)

### Itération 5 : Premier déploiement OpenShift

**US-06 — Déployer la GED sur OpenShift** · M
> En tant qu'**exploitant**, je veux déployer la GED par un chart Helm afin d'avoir un déploiement reproductible.

Critères d'acceptation :
- `helm install` sur un namespace de recette déploie l'API, joignable par une Route HTTPS.
- Les probes liveness et readiness sont branchées sur l'actuator.
- Les secrets (base, Keycloak) viennent de Secrets OpenShift, jamais du dépôt.
- Les images sont tirées du **registre interne**.

Tâches :
- [ ] T06.1 Écrire le `Dockerfile` de `ged-api` (image de base UBI du registre interne, utilisateur non-root)
- [ ] T06.2 Créer le chart `deploy/helm/ged` : Deployment, Service, Route TLS, ConfigMap
- [ ] T06.3 Brancher les probes liveness et readiness sur `/actuator/health/liveness|readiness`
- [ ] T06.4 Passer base et Keycloak par des Secrets référencés (`existingSecret`), sans valeur par défaut dans le chart
- [ ] T06.5 Ajouter `values-recette.yaml` (namespace, registre, URLs internes)
- [ ] T06.6 Construire et pousser l'image vers le registre interne dans la CI
- [ ] T06.7 Rédiger `docs/exploitation/deploiement.md` (procédure `helm install/upgrade`)
- [ ] T06.8 Déployer en recette et valider `/ping` via la Route

**US-07 — Garantir qu'aucune donnée ne sort** · S
> En tant que **responsable conformité**, je veux que la GED ne puisse joindre aucun service externe afin de respecter nos obligations légales.

Critères d'acceptation :
- Une NetworkPolicy de sortie refuse tout, sauf les services internes déclarés.
- Un test de recette vérifie qu'un appel vers Internet depuis le pod échoue.

Tâches :
- [ ] T07.1 Écrire la NetworkPolicy *egress* qui refuse tout par défaut
- [ ] T07.2 Ajouter des NetworkPolicies d'autorisation explicites (DNS, PostgreSQL, Keycloak, puis les autres services au fil des itérations)
- [ ] T07.3 Rendre la liste des destinations autorisées paramétrable dans les `values`
- [ ] T07.4 Écrire le test de recette (Job `curl` vers une URL publique → doit échouer)
- [ ] T07.5 Écrire l'ADR `docs/adr/0002-aucune-sortie-reseau.md`

---

## B. Documents

### Itération 6 : Dépôt et téléchargement

**US-08 — Déposer un document** · M
> En tant qu'**utilisateur**, je veux déposer un fichier dans un dossier afin de le conserver dans la GED.

Critères d'acceptation :
- `POST /api/v1/documents` crée la fiche document (titre, dossier). `POST /documents/{id}/content` envoie le fichier.
- Le fichier est **transmis en flux** vers MinIO, sans être chargé en mémoire. Au-delà de 100 Mo, la réponse est `413`.
- Le type MIME et la taille sont enregistrés.

Tâches :
- [ ] T08.1 Ajouter MinIO au docker-compose (bucket `ged-content` créé au démarrage)
- [ ] T08.2 Ajouter AWS SDK v2 S3 et configurer le client (endpoint, *path-style*, identifiants)
- [ ] T08.3 Définir le port `ContentStorage` et l'implémentation `S3ContentStorage`
- [ ] T08.4 Migration `V2__documents.sql` : table `document` (id, folder_id, title, mime, size, storage_key, created_at/by)
- [ ] T08.5 Écrire l'entité, le repository et `DocumentService.create`
- [ ] T08.6 Écrire `POST /documents/{id}/content` : transmission en flux de `InputStream` vers S3, avec taille max 100 Mo → 413
- [ ] T08.7 Détecter le type MIME (Tika `detect`)
- [ ] T08.8 Mettre à jour `ged-v1.yaml` (documents, content)
- [ ] T08.9 Écrire les tests (Testcontainers MinIO) : dépôt, dépassement de taille, document inexistant → 404

**US-09 — Télécharger un document** · S
> En tant qu'**utilisateur**, je veux télécharger un document afin de le consulter avec mon logiciel habituel.

Critères d'acceptation :
- `GET /documents/{id}/content` renvoie une redirection `302` vers une URL présignée de courte durée.
- Le fichier téléchargé est identique, octet pour octet, à celui déposé.

Tâches :
- [ ] T09.1 Ajouter à `ContentStorage` une méthode `presignedGetUrl(key, ttl, filename)`
- [ ] T09.2 Écrire `GET /documents/{id}/content` → 302 avec `Location` présignée et `Content-Disposition`
- [ ] T09.3 Rendre la durée de validité configurable (`ged.storage.presign-ttl`, par défaut 5 minutes)
- [ ] T09.4 Écrire le test : télécharger via l'URL et comparer les octets

### Itération 7 : Versions et déduplication

**US-10 — Gérer les versions d'un document** · M
> En tant qu'**utilisateur**, je veux déposer une nouvelle version d'un document afin de garder l'historique de ses modifications.

Critères d'acceptation :
- Chaque dépôt sur un document existant crée la version n+1, avec auteur et date.
- `GET /documents/{id}/versions` liste les versions, et chacune reste téléchargeable.
- La version courante est la dernière.

Tâches :
- [ ] T10.1 Migration `V3__versions.sql` : table `document_version` (n°, storage_key, sha256, size, mime, created_at/by) et `document.current_version_id`
- [ ] T10.2 Faire créer une nouvelle version par chaque dépôt de contenu
- [ ] T10.3 Écrire `GET /documents/{id}/versions` et `GET /documents/{id}/versions/{n}/content`
- [ ] T10.4 Faire pointer le téléchargement par défaut vers la version courante
- [ ] T10.5 Mettre à jour `ged-v1.yaml`
- [ ] T10.6 Écrire les tests : 2 dépôts → 2 versions, téléchargement de la v1 après dépôt de la v2

**US-11 — Ne pas stocker deux fois le même fichier** · S
> En tant qu'**exploitant**, je veux qu'un fichier identique ne soit stocké qu'une fois afin d'économiser l'espace disque.

Critères d'acceptation :
- Le SHA-256 est calculé pendant l'envoi, et la clé S3 en est dérivée.
- Deux dépôts identiques produisent un seul objet S3.

Tâches :
- [ ] T11.1 Calculer le SHA-256 en flux (`DigestInputStream`) pendant l'envoi
- [ ] T11.2 Uploader vers une clé temporaire, puis copier vers `content/<sha256[0:2]>/<sha256>` si l'objet n'existe pas, et supprimer la clé temporaire
- [ ] T11.3 Gérer la concurrence : deux dépôts identiques simultanés ne doivent pas poser problème
- [ ] T11.4 Écrire le test : 2 dépôts identiques → 1 seul objet dans le bucket
- [ ] T11.5 Écrire l'ADR `0003-stockage-adresse-par-contenu.md`

### Itération 8 : Gros fichiers

**US-12 — Déposer des fichiers volumineux** · M
> En tant qu'**utilisateur**, je veux déposer des fichiers de plusieurs Go sans coupure afin de numériser de gros dossiers.

Critères d'acceptation :
- `POST /uploads` initialise un upload multipart et renvoie des URLs présignées par partie. Un appel `complete` finalise l'upload.
- Les octets vont directement du client vers S3 : l'API ne les voit pas.
- Un fichier de 2 Go se dépose. Un upload interrompu peut reprendre les parties manquantes.
- Les uploads abandonnés sont nettoyés automatiquement.

Tâches :
- [ ] T12.1 Écrire `POST /uploads` : création de l'upload multipart S3, calcul des parties, URLs présignées `UploadPart`
- [ ] T12.2 Écrire `POST /uploads/{id}/complete` : `CompleteMultipartUpload`, puis création de la version
- [ ] T12.3 Écrire `GET /uploads/{id}`, qui liste les parties déjà reçues (reprise)
- [ ] T12.4 Calculer le SHA-256 après assemblage (lecture en flux de l'objet par le serveur) et appliquer la déduplication
- [ ] T12.5 Migration : table `upload_session` (état, expiration)
- [ ] T12.6 Écrire le job de nettoyage des uploads abandonnés (`AbortMultipartUpload`) et la règle de cycle de vie du bucket
- [ ] T12.7 Configurer CORS du bucket pour le frontend
- [ ] T12.8 Écrire le test d'intégration : fichier de 50 Mo en 5 parties (le test à 2 Go est manuel, en recette)

### Itération 9 : Droits d'accès

**US-13 — Restreindre l'accès aux documents** · L
> En tant qu'**administrateur fonctionnel**, je veux donner des droits (lecture, écriture, suppression, administration) à des utilisateurs ou groupes sur un dossier afin de protéger les documents sensibles.

Critères d'acceptation :
- Les droits posés sur un dossier sont **hérités** par ses sous-dossiers et documents.
- Un droit peut être posé directement sur un document.
- Sans droit READ, un document ou un dossier renvoie `404`, ce qui ne révèle pas son existence.
- Sans droit WRITE, une modification renvoie `403`.
- `GET/PUT /folders/{id}/acl` et `/documents/{id}/acl` sont disponibles.

Notes techniques : `ltree` pour l'arborescence. La vérification des droits ne doit pas parcourir les ancêtres un par un.

Tâches :
- [ ] T13.1 Migration : extension `ltree`, colonne `folder.path`, table `acl_entry` (principal_type, principal_id, resource_type, resource_id, permission)
- [ ] T13.2 Maintenir `path` à la création et au déplacement d'un dossier
- [ ] T13.3 Écrire `PermissionService.check(user, resource, permission)` : une seule requête SQL sur les ancêtres via `path @>`
- [ ] T13.4 Écrire `PermissionService.filter(...)` pour les listes (jointure SQL, pas de filtrage en mémoire)
- [ ] T13.5 Appliquer les contrôles dans `FolderService` et `DocumentService` (404 sans READ, 403 sans WRITE ou DELETE)
- [ ] T13.6 Donner automatiquement le droit ADMIN au créateur, ou le droit hérité de la racine
- [ ] T13.7 Écrire les endpoints `GET/PUT /folders/{id}/acl` et `/documents/{id}/acl`
- [ ] T13.8 Écrire les tests : héritage, droit direct sur un document, utilisateur sans droit, droit via un groupe

### Itération 10 : Audit

**US-14 — Tracer toutes les actions** · M
> En tant que **responsable conformité**, je veux savoir qui a fait quoi et quand sur chaque document afin de répondre à un contrôle.

Critères d'acceptation :
- Création, consultation, téléchargement, modification, changement de droits et suppression produisent un événement d'audit (acteur, action, cible, date, IP, client API).
- `GET /documents/{id}/audit` renvoie l'historique du document.
- La table est **append-only** : aucune mise à jour ni suppression, ce qui est garanti en base.

Tâches :
- [ ] T14.1 Migration : table `audit_event` (id, ts, actor, client_id, ip, action, resource_type, resource_id, details JSONB)
- [ ] T14.2 Écrire `AuditService.record(...)`, appelé dans la transaction métier
- [ ] T14.3 Instrumenter les actions : création, consultation, téléchargement, modification, droits, suppression
- [ ] T14.4 Interdire UPDATE et DELETE par un trigger PostgreSQL, avec un utilisateur applicatif sans ces droits
- [ ] T14.5 Écrire `GET /documents/{id}/audit` (paginé)
- [ ] T14.6 Écrire les tests : chaque action produit son événement, et un UPDATE manuel échoue

**US-15 — Rendre l'audit infalsifiable** · S
> En tant que **responsable conformité**, je veux détecter toute altération du journal d'audit afin de pouvoir lui faire confiance.

Critères d'acceptation :
- Chaque événement contient le hash de l'événement précédent.
- Une vérification détecte une ligne modifiée ou supprimée.
- La table est partitionnée par mois.

Tâches :
- [ ] T15.1 Ajouter les colonnes `prev_hash` et `hash` (SHA-256 du contenu canonique + `prev_hash`)
- [ ] T15.2 Sérialiser l'insertion pour garantir la chaîne (verrou consultatif ou séquence dédiée)
- [ ] T15.3 Partitionner par mois (`PARTITION BY RANGE (ts)`) et créer les partitions automatiquement
- [ ] T15.4 Écrire `AuditVerifier`, qui recalcule la chaîne sur une période
- [ ] T15.5 Écrire le test : altération d'une ligne (en contournant le trigger dans le test) → vérification en échec

---

## C. Schéma configurable

### Itération 11 : Classes et champs

**US-16 — Créer une classe de documents avec ses champs** · L
> En tant qu'**administrateur fonctionnel**, je veux créer une classe de documents (ex. « Facture ») et ses champs d'indexation, sans faire appel aux développeurs, afin d'adapter la GED à mon entreprise.

Critères d'acceptation :
- CRUD `/classes` et `/fields` (rôle `ged-admin`).
- Types disponibles dans cette itération : texte court ou long, entier, décimal, montant, date, booléen.
- Propriétés : code immuable, libellés fr/de/en, obligatoire, multi-valué, défaut, regex, min/max.
- La modification prend effet **sans redémarrage**.

Tâches :
- [ ] T16.1 Migration : tables `document_class` et `field_definition`, avec `class_field`
- [ ] T16.2 Écrire le modèle Java `FieldType` (enum) et les propriétés de champ (record + JSONB `constraints`)
- [ ] T16.3 Écrire le CRUD `/classes` et `/fields` (rôle `ged-admin`), avec code immuable et libellés fr/de/en
- [ ] T16.4 Écrire le cache `SchemaCache` (Caffeine), invalidé à chaque modification
- [ ] T16.5 Mettre à jour `ged-v1.yaml` (schéma d'administration)
- [ ] T16.6 Écrire les tests : création, modification de libellé, interdiction de modifier le code

**US-17 — Indexer un document selon sa classe** · M
> En tant qu'**utilisateur**, je veux saisir les champs d'un document selon sa classe afin de le retrouver facilement.

Critères d'acceptation :
- Un document porte une classe, et ses valeurs vont dans `metadata` (JSONB).
- Des valeurs non conformes renvoient `422`, avec le détail champ par champ.
- `PATCH /documents/{id}/metadata` modifie les valeurs.

Tâches :
- [ ] T17.1 Ajouter `document.class_id` et `document.metadata JSONB`, avec index GIN `jsonb_path_ops`
- [ ] T17.2 Écrire `MetadataValidator` : type, obligatoire, regex, min/max, multi-valué, défaut
- [ ] T17.3 Renvoyer `422` avec `errors[]` par champ (extension de ProblemDetail)
- [ ] T17.4 Écrire `PATCH /documents/{id}/metadata` (JSON Merge Patch)
- [ ] T17.5 Écrire les tests paramétrés sur chaque type de champ et chaque contrainte

### Itération 12 : Référentiels et héritage

**US-18 — Proposer des listes de valeurs** · M
> En tant qu'**administrateur fonctionnel**, je veux définir des listes de valeurs (ex. fournisseurs, services) afin que les utilisateurs choisissent au lieu de saisir.

Critères d'acceptation :
- CRUD `/value-lists`, avec des valeurs éventuellement hiérarchiques et la possibilité de désactiver une valeur sans la supprimer.
- Un champ de type « liste » n'accepte que des valeurs actives.

Tâches :
- [ ] T18.1 Migration : tables `value_list` et `value_list_item` (code, libellé, parent, actif)
- [ ] T18.2 Écrire le CRUD `/value-lists` et `/value-lists/{id}/items`
- [ ] T18.3 Ajouter le type de champ `LIST`, référencé vers une liste, avec validation sur les valeurs actives
- [ ] T18.4 Écrire les tests : valeur inconnue ou désactivée → 422

**US-19 — Faire hériter une classe d'une autre** · M
> En tant qu'**administrateur fonctionnel**, je veux qu'une classe hérite des champs d'une classe parente afin d'éviter de les redéfinir.

Critères d'acceptation :
- « Facture fournisseur » hérite des champs de « Document comptable ».
- Une classe enfant peut rendre obligatoire un champ hérité, et changer son ordre.
- Nouveaux types de champs : utilisateur/groupe, lien vers un autre document.
- `GET /classes/{id}/json-schema` renvoie le schéma complet, héritage compris.

Tâches :
- [ ] T19.1 Ajouter `document_class.parent_id` et la résolution des champs effectifs (héritage + surcharges de `class_field`)
- [ ] T19.2 Interdire les cycles d'héritage
- [ ] T19.3 Ajouter les types `USER_GROUP` et `DOCUMENT_LINK`, avec la validation correspondante (existence, droit READ sur la cible)
- [ ] T19.4 Écrire `JsonSchemaGenerator` → `GET /classes/{id}/json-schema`
- [ ] T19.5 Écrire les tests : héritage sur 3 niveaux, surcharge de « obligatoire », JSON Schema conforme

### Itération 13 : Versionnage et export

**US-20 — Transporter la configuration entre environnements** · M
> En tant qu'**administrateur fonctionnel**, je veux exporter la configuration de recette et l'importer en production afin de ne pas tout ressaisir.

Critères d'acceptation :
- `GET /schema/export` produit un YAML complet : classes, champs, listes.
- `POST /schema/import` l'applique sur une base vierge ou existante, avec un rapport des différences.
- Chaque modification du schéma crée une révision tracée dans l'audit.

Tâches :
- [ ] T20.1 Migration : table `schema_revision` (n°, auteur, date, diff JSON)
- [ ] T20.2 Créer une révision et un événement d'audit à chaque modification du schéma
- [ ] T20.3 Écrire `SchemaExporter` (YAML via Jackson YAML), avec un format documenté dans `docs/schema-format.md`
- [ ] T20.4 Écrire `SchemaImporter` : calcul du diff, mode simulation, application transactionnelle, rapport
- [ ] T20.5 Écrire `GET /schema/export` et `POST /schema/import?dryRun=`
- [ ] T20.6 Écrire le test aller-retour : export → import sur base vierge → export identique

---

## D. Premier frontend

### Itération 14 : Squelette et navigation

**US-21 — Naviguer dans les dossiers et déposer des fichiers** · L
> En tant qu'**utilisateur**, je veux naviguer dans l'arborescence et déposer des fichiers par glisser-déposer afin d'utiliser la GED sans outil technique.

Critères d'acceptation :
- Connexion via Keycloak (OIDC), déconnexion.
- Arborescence des dossiers, avec liste des documents paginée.
- Glisser-déposer d'un ou plusieurs fichiers, avec barre de progression, en multipart présigné.
- Le client API est **généré** depuis `ged-v1.yaml`.
- Aucune ressource n'est chargée depuis un CDN.

Tâches :
- [ ] T21.1 Initialiser `ged-web` (Vite + React + TypeScript, ESLint, Prettier), avec le miroir npm interne
- [ ] T21.2 Générer le client TypeScript depuis `ged-v1.yaml` (script npm)
- [ ] T21.3 Brancher l'authentification OIDC (`oidc-client-ts`, PKCE) et le contexte utilisateur
- [ ] T21.4 Configurer TanStack Query et le routeur (React Router)
- [ ] T21.5 Créer la mise en page : en-tête, arborescence, zone principale
- [ ] T21.6 Écrire le composant arborescence (chargement paresseux des enfants) et la liste des documents (pagination par curseur)
- [ ] T21.7 Écrire le composant de dépôt par glisser-déposer : multipart présigné, progression par partie, reprise
- [ ] T21.8 Embarquer les polices et ressources, et vérifier l'absence de CDN (CSP stricte)
- [ ] T21.9 Servir `ged-web` (Nginx UBI dans le chart Helm)
- [ ] T21.10 Écrire les tests de composants (Vitest + Testing Library)

### Itération 15 : Formulaires générés

**US-22 — Saisir l'indexation avec un formulaire adapté à la classe** · M
> En tant qu'**utilisateur**, je veux un formulaire qui affiche exactement les champs de la classe choisie afin de saisir rapidement les bonnes informations.

Critères d'acceptation :
- Le formulaire est **généré** à partir de `/schema` : bon type de saisie, listes déroulantes, champs obligatoires signalés.
- Les erreurs de validation du serveur s'affichent sous chaque champ.
- Un champ ajouté par l'administrateur apparaît sans redéployer le frontend.

Tâches :
- [ ] T22.1 Écrire le hook `useSchema()` (cache TanStack Query)
- [ ] T22.2 Écrire `DynamicForm` : un composant de saisie par `FieldType` (texte, nombre, montant, date, booléen, liste, utilisateur, lien)
- [ ] T22.3 Valider côté client à partir du JSON Schema, avec affichage des erreurs `422` du serveur
- [ ] T22.4 Écrire la fiche document : métadonnées, versions, changement de classe
- [ ] T22.5 Écrire le test : un champ ajouté au schéma apparaît sans rebuild

**US-23 — Administrer classes et champs depuis l'interface** · M
> En tant qu'**administrateur fonctionnel**, je veux un écran pour gérer classes, champs et listes de valeurs afin de ne pas passer par l'API.

Tâches :
- [ ] T23.1 Écrire les écrans d'administration : liste des classes, éditeur de classe (héritage, champs, ordre par glisser-déposer)
- [ ] T23.2 Écrire l'éditeur de champ (type, contraintes, options de recherche, consigne IA)
- [ ] T23.3 Écrire l'éditeur de listes de valeurs
- [ ] T23.4 Écrire l'écran export/import YAML, avec aperçu du diff
- [ ] T23.5 Masquer l'administration aux utilisateurs sans `ged-admin`

---

## E. Pipeline et recherche

### Itération 16 : Traitements asynchrones

**US-24 — Traiter les documents en arrière-plan** · L
> En tant qu'**utilisateur**, je veux que mon dépôt soit immédiat, même si des traitements longs suivent, afin de ne pas attendre.

Critères d'acceptation :
- Le dépôt écrit un événement dans l'**outbox**, dans la même transaction.
- Un relais publie l'événement dans Kafka. Le nouveau module `ged-worker` le consomme.
- Une panne de Kafka ne perd aucun événement : ils sont publiés au redémarrage.
- Un message en échec part en **DLQ**, et un rejeu est possible.

Tâches :
- [ ] T24.1 Ajouter Kafka au docker-compose (mode KRaft)
- [ ] T24.2 Migration : table `outbox_event` (id, aggregate, type, payload, created_at, published_at)
- [ ] T24.3 Écrire `OutboxWriter`, appelé dans la transaction de dépôt (`document.version.created`)
- [ ] T24.4 Écrire `OutboxRelay` : publication par lots (`SELECT … FOR UPDATE SKIP LOCKED`), marquage, purge
- [ ] T24.5 Créer le module `ged-worker` (Spring Boot + Spring Kafka), qui dépend de `ged-core`
- [ ] T24.6 Extraire le code partagé de `ged-api` vers `ged-core` (entités, repositories, stockage)
- [ ] T24.7 Écrire un consommateur `version.created` qui journalise l'événement
- [ ] T24.8 Configurer la DLQ (`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`) et l'endpoint d'administration de rejeu
- [ ] T24.9 Ajouter `ged-worker` au chart Helm (Deployment séparé)
- [ ] T24.10 Écrire les tests : Kafka arrêté → pas de perte, message en erreur → DLQ

### Itération 17 : Texte et OCR

**US-25 — Extraire le texte des documents** · M
> En tant qu'**utilisateur**, je veux que le texte de mes documents soit extrait, y compris pour les scans, afin de pouvoir y chercher des mots.

Critères d'acceptation :
- Tika extrait le texte des PDF texte, Word, emails…
- Sans texte (scan, photo), Tesseract fait l'OCR en français.
- `GET /documents/{id}/text` renvoie le texte. L'état du traitement est visible sur la version.

Tâches :
- [ ] T25.1 Ajouter Tika (`tika-core` + parsers) à `ged-worker`
- [ ] T25.2 Ajouter Tesseract + `tesseract-ocr-fra`, `-deu`, `-eng` dans l'image du worker
- [ ] T25.3 Écrire `TextExtractionStep` : Tika d'abord, et si le texte est trop pauvre, OCR (via `ocrmypdf`/Tesseract) page par page
- [ ] T25.4 Stocker le texte extrait dans S3 (`text/<sha256>.txt`) et son statut sur `document_version`
- [ ] T25.5 Écrire `GET /documents/{id}/text`
- [ ] T25.6 Paramétrer les langues OCR, le DPI et le délai maximal
- [ ] T25.7 Écrire les tests avec un PDF texte, un scan et un `.docx` en fixtures

### Itération 18 : Recherche plein texte

**US-26 — Rechercher un document par son contenu** · M
> En tant qu'**utilisateur**, je veux taper des mots et retrouver les documents qui les contiennent afin de ne plus fouiller les dossiers.

Critères d'acceptation :
- Chaque document est indexé dans OpenSearch (texte, titre, métadonnées), via un alias.
- `POST /search` gère le plein texte avec surlignage des extraits et la pagination `search_after`.
- Un scan est retrouvé par un mot de son contenu.

Tâches :
- [ ] T26.1 Ajouter OpenSearch au docker-compose, avec le client Java OpenSearch
- [ ] T26.2 Créer l'index `ged-documents-v1` + alias `ged-documents` (mapping de base : titre, texte, classe, dates)
- [ ] T26.3 Écrire `IndexingStep`, qui consomme `text.extracted` et indexe le document
- [ ] T26.4 Écrire `POST /search` : `multi_match`, `highlight`, pagination `search_after`
- [ ] T26.5 Mettre à jour `ged-v1.yaml` (requête et réponse de recherche)
- [ ] T26.6 Écrire le test : un scan est retrouvé par un mot OCRisé

### Itération 19 : Facettes et sécurité de la recherche

**US-27 — Affiner une recherche avec des facettes** · L
> En tant qu'**utilisateur**, je veux filtrer les résultats par classe, date, fournisseur… avec le nombre de documents pour chaque valeur, afin de trouver vite parmi des millions de documents.

Critères d'acceptation :
- Chaque champ configuré a un mapping typé sous `meta.<code>`, créé automatiquement.
- Les champs marqués « facette » renvoient des agrégations, et ceux marqués « filtrable » sont filtrables.
- Le mapping est strict : un champ non déclaré est refusé.

Tâches :
- [ ] T27.1 Écrire `MappingSynchronizer` : `PUT _mapping` additif pour `meta.<code>` selon le `FieldType`
- [ ] T27.2 Passer le mapping en `dynamic: strict` et surveiller `total_fields`
- [ ] T27.3 Indexer `metadata` sous `meta.*`
- [ ] T27.4 Ajouter à `/search` les filtres et facettes (agrégations `terms`, `range`, `date_histogram`) selon les propriétés des champs
- [ ] T27.5 Écrire les tests : un nouveau champ « facette » produit une agrégation sans redéploiement

**US-28 — Ne jamais voir un document interdit dans la recherche** · M
> En tant que **responsable conformité**, je veux que la recherche ne montre jamais un document auquel l'utilisateur n'a pas droit, même dans les compteurs des facettes.

Critères d'acceptation :
- Les principaux autorisés sont indexés, et le filtre est appliqué **dans la requête**.
- Un changement de droits met à jour l'index.
- Les compteurs de facettes n'incluent que les documents autorisés.

Tâches :
- [ ] T28.1 Indexer `allowed_principals` (utilisateurs et groupes ayant READ, héritage résolu)
- [ ] T28.2 Appliquer le filtre `terms` sur les principaux de l'utilisateur dans **toutes** les requêtes, facettes comprises
- [ ] T28.3 Écrire l'événement `acl.changed`, qui réindexe les documents concernés (sous-arbre d'un dossier : `update_by_query` par lots)
- [ ] T28.4 Écrire les tests : un document interdit n'apparaît ni dans les résultats ni dans les compteurs

### Itération 20 : Changement de schéma

**US-29 — Modifier un champ sans casser la recherche** · M
> En tant qu'**administrateur fonctionnel**, je veux pouvoir changer le type d'un champ afin de corriger une erreur de configuration.

Critères d'acceptation :
- Un changement cassant lance une réindexation vers un nouvel index, puis la bascule de l'alias, sans interruption de la recherche.
- L'avancement est visible.
- Le rapport liste les documents dont la valeur n'est pas convertible.

Tâches :
- [ ] T29.1 Détecter les changements cassants dans `SchemaService`
- [ ] T29.2 Écrire `ReindexJob` : création de `ged-documents-vN+1`, `_reindex` ou réindexation depuis PostgreSQL, bascule atomique de l'alias
- [ ] T29.3 Suivre l'avancement (`GET /admin/reindex/{id}`) et produire le rapport des valeurs non convertibles
- [ ] T29.4 Écrire le test : changement texte → date, avec la recherche disponible pendant la réindexation

### Itération 21 : Écran de recherche

**US-30 — Rechercher depuis l'interface** · M
> En tant qu'**utilisateur**, je veux un écran de recherche avec facettes cliquables afin de chercher sans connaître l'API.

Critères d'acceptation :
- Barre de recherche, résultats avec extraits surlignés.
- Facettes générées à partir du schéma, avec compteurs mis à jour à chaque clic.
- Colonnes de résultats selon les champs marqués « colonne », avec tri.

Tâches :
- [ ] T30.1 Écrire la page de recherche : barre, résultats avec extraits surlignés
- [ ] T30.2 Écrire le panneau de facettes généré depuis le schéma, avec compteurs et sélection multiple
- [ ] T30.3 Écrire les colonnes dynamiques et le tri
- [ ] T30.4 Synchroniser les critères dans l'URL (partage de recherche)

### Itération 22 : Prévisualisation

**US-31 — Prévisualiser un document dans le navigateur** · L
> En tant qu'**utilisateur**, je veux voir un PDF ou un document Word directement dans la GED afin de ne pas avoir à le télécharger.

Critères d'acceptation :
- Les fichiers Word, Excel, PowerPoint, ODF et RTF sont convertis en PDF par Gotenberg, sur site. Le PDF est linéarisé par `qpdf`.
- Les PDF et TIFF sont affichés, tout comme les images.
- Visionneuse PDF.js : pages, zoom, recherche, rotation, plein écran. Chargement progressif par requêtes Range.
- `GET /documents/{id}/preview` renvoie `READY`, `PENDING` ou `UNSUPPORTED`. Une miniature est générée.
- L'original n'est jamais modifié. Un fichier corrompu donne « aperçu indisponible ».
- Les termes recherchés sont surlignés à l'ouverture depuis la recherche.

Tâches :
- [ ] T31.1 Ajouter Gotenberg au docker-compose et au chart (Deployment + HPA)
- [ ] T31.2 Écrire `RenditionStep` : PDF → copie, Office/ODF/RTF → Gotenberg, TIFF/images → PDF, puis linéarisation `qpdf`
- [ ] T31.3 Stocker `renditions/<sha256>.pdf` et le statut sur la version, avec délai maximal et DLQ
- [ ] T31.4 Générer la miniature PNG (première page, PDFBox) → `GET /documents/{id}/thumbnail`
- [ ] T31.5 Écrire `GET /documents/{id}/preview` (READY, PENDING, UNSUPPORTED) et la génération à la demande
- [ ] T31.6 Écrire `ViewerPdf` (PDF.js + worker embarqué) : pagination, zoom, recherche, rotation, plein écran, requêtes Range
- [ ] T31.7 Transmettre les termes de recherche à la visionneuse pour le surlignage
- [ ] T31.8 Configurer les en-têtes CORS et Range du bucket pour PDF.js
- [ ] T31.9 Écrire les tests : `.docx`, `.doc`, `.xlsx`, TIFF multipage, fichier corrompu

**US-32 — Filigraner les documents confidentiels** · S
> En tant qu'**administrateur fonctionnel**, je veux activer un filigrane (nom, date, « Confidentiel ») pour certaines classes afin de dissuader les fuites.

Tâches :
- [ ] T32.1 Ajouter la propriété `watermark` (activé, modèle de texte) sur `document_class`
- [ ] T32.2 Dessiner le filigrane dans la visionneuse (calque canvas au-dessus de chaque page)
- [ ] T32.3 Exposer l'information de filigrane dans `/preview`

### Itération 23 : Antivirus

**US-33 — Bloquer les fichiers infectés** · M
> En tant qu'**exploitant**, je veux que chaque fichier soit analysé par un antivirus avant tout traitement afin de protéger les postes des utilisateurs.

Critères d'acceptation :
- ClamAV analyse chaque nouvelle version, avec des signatures issues du miroir interne.
- Un fichier infecté (test EICAR) est mis en quarantaine, n'est jamais téléchargeable, et l'événement est audité.
- Les traitements suivants (OCR, rendition) n'ont lieu qu'après une analyse saine.

Tâches :
- [ ] T33.1 Ajouter ClamAV au docker-compose et au chart (signatures depuis le miroir interne, freshclam configuré)
- [ ] T33.2 Écrire `AntivirusStep` (protocole clamd `INSTREAM`), comme première étape du pipeline
- [ ] T33.3 Ajouter le statut `INFECTED` : quarantaine, blocage du téléchargement et de la prévisualisation, audit, notification
- [ ] T33.4 Enchaîner les étapes : OCR et rendition seulement après `CLEAN`
- [ ] T33.5 Écrire le test avec le fichier EICAR

---

## F. IA

### Itération 24 : Classification locale

**US-34 — Pré-remplir automatiquement l'indexation** · L
> En tant qu'**utilisateur**, je veux que la GED propose la classe et remplisse les champs d'un document déposé afin de gagner du temps de saisie.

Critères d'acceptation :
- `OllamaClassifier` appelle l'**Ollama + Gemma 4 interne**, avec une sortie JSON contrainte par le schéma de la classe.
- La consigne d'extraction de chaque champ est utilisée dans le prompt.
- Le traitement est asynchrone, avec une concurrence configurable. Le document est cherchable avant d'être classé.
- L'IA se désactive par `ged.ai.enabled=false`.
- Tout fonctionne avec la NetworkPolicy qui refuse toute sortie.

Tâches :
- [ ] T34.1 Configurer Spring AI (module Ollama) : `ged.ai.base-url`, `ged.ai.model`, `ged.ai.enabled`
- [ ] T34.2 Définir l'interface `DocumentClassifier` et le résultat `Classification` (classe, valeurs, confiance par champ)
- [ ] T34.3 Écrire `PromptBuilder` : classes candidates, consignes d'extraction, texte tronqué à N pages
- [ ] T34.4 Écrire `OllamaClassifier`, avec `format` = JSON Schema dynamique (structured outputs)
- [ ] T34.5 Écrire `ClassificationStep` : topic dédié, concurrence configurable, priorité aux nouveaux dépôts
- [ ] T34.6 Appliquer le résultat (si confiance ≥ seuil) avec audit (« proposé par IA »)
- [ ] T34.7 Créer un jeu d'évaluation (20 documents annotés) et un rapport de précision
- [ ] T34.8 Écrire les tests avec un Ollama simulé (WireMock), plus un test manuel sur le vrai Gemma 4

**US-35 — Valider les propositions incertaines** · M
> En tant qu'**utilisateur**, je veux revoir les propositions de l'IA dont elle n'est pas sûre afin de corriger les erreurs avant qu'elles ne se propagent.

Critères d'acceptation :
- En dessous du seuil de confiance, le document entre dans la file `/classification-review`.
- On peut accepter, corriger ou rejeter la proposition. La décision est auditée.
- `POST /documents/{id}/reclassify` relance la classification.

Tâches :
- [ ] T35.1 Migration : table `classification_review` (document, proposition, confiance, statut)
- [ ] T35.2 Écrire `/classification-review` : liste, accepter, corriger, rejeter
- [ ] T35.3 Écrire `POST /documents/{id}/reclassify`
- [ ] T35.4 Écrire l'écran frontend de revue : proposition et visionneuse côte à côte

---

## G. Workflows

### Itération 25 : Premier circuit

**US-36 — Soumettre un document à validation** · L
> En tant qu'**utilisateur**, je veux qu'une facture déposée parte automatiquement en validation afin qu'elle soit approuvée par la bonne personne.

Critères d'acceptation :
- Flowable est embarqué. Le modèle « Validation simple » est fourni.
- Il s'affecte à une classe de documents, avec son groupe valideur et son délai.
- Le dépôt d'un document de cette classe démarre le circuit.

Tâches :
- [ ] T36.1 Ajouter `flowable-spring-boot-starter-process` à `ged-api` (tables gérées par Flowable)
- [ ] T36.2 Écrire `validation-simple.bpmn` (user task pour un groupe candidat, passerelle approuvé/rejeté, timer de délai)
- [ ] T36.3 Migration : table `workflow_assignment` (classe → définition + paramètres JSON)
- [ ] T36.4 Écrire le démarrage automatique au dépôt, sur l'événement `document.created` si la classe a un workflow
- [ ] T36.5 Écrire `/workflow-templates` et l'affectation à une classe

**US-37 — Traiter mes tâches de validation** · M
> En tant que **valideur**, je veux voir mes tâches et approuver, rejeter avec un commentaire ou déléguer afin de traiter les validations rapidement.

Critères d'acceptation :
- `GET /tasks` liste mes tâches et celles de mes groupes.
- `POST /tasks/{id}/complete` approuve ou rejette. Un rejet renvoie le document au déposant, avec le commentaire.
- Notification par email (SMTP interne) et relance quand le délai est dépassé.
- Chaque étape est auditée.
- Écran « Mes tâches » dans le frontend.

Tâches :
- [ ] T37.1 Écrire `GET /tasks` (candidat ou assigné), `POST /tasks/{id}/claim|complete|delegate`
- [ ] T37.2 Enregistrer les commentaires de rejet et le retour au déposant
- [ ] T37.3 Configurer les notifications email (Spring Mail, SMTP interne, modèles Thymeleaf)
- [ ] T37.4 Écrire les timers d'escalade (relance et réaffectation)
- [ ] T37.5 Écrire un `ExecutionListener` qui audite chaque transition
- [ ] T37.6 Écrire l'écran frontend « Mes tâches » et l'action depuis la fiche document
- [ ] T37.7 Écrire le test de bout en bout : dépôt → tâche → approbation → statut « Validé »

### Itération 26 : Workflows sûrs et modèles par défaut

**US-38 — Disposer de workflows prêts à l'emploi** · M
> En tant qu'**administrateur fonctionnel**, je veux des circuits standards prêts à l'emploi afin de démarrer sans rien dessiner.

Critères d'acceptation :
- Les 6 modèles sont installés au premier démarrage :
  - validation simple ;
  - double validation ;
  - validation selon un seuil ;
  - validation collégiale ;
  - relecture et publication ;
  - prise de connaissance.
- Ils sont paramétrables à l'affectation (groupes, seuil, champ, délai, quorum), protégés et duplicables.

Tâches :
- [ ] T38.1 Écrire les 5 autres BPMN : double validation, validation selon un seuil, validation collégiale (multi-instance + quorum), relecture et publication, prise de connaissance
- [ ] T38.2 Écrire le déploiement au premier démarrage, avec marquage « protégé » et versionnage des modèles
- [ ] T38.3 Écrire la duplication d'un modèle en brouillon modifiable
- [ ] T38.4 Écrire le formulaire de paramétrage à l'affectation (groupes, seuil, champ, délai, quorum)
- [ ] T38.5 Écrire les tests pour chaque modèle (chemins nominal et rejet)

**US-39 — Empêcher l'exécution de code dans les workflows** · M
> En tant qu'**exploitant**, je veux que seuls des circuits sûrs puissent être publiés afin qu'un workflow ne puisse pas compromettre le serveur.

Critères d'acceptation :
- Validation côté serveur : liste blanche des éléments BPMN et des delegates GED, script tasks refusées, existence des champs référencés.
- Un BPMN invalide renvoie `422`, avec la liste des problèmes.

Tâches :
- [ ] T39.1 Écrire les delegates GED (`SetStatusDelegate`, `LockDelegate`, `UpdateFieldDelegate`, `NotifyDelegate`, `ArchiveDelegate`)
- [ ] T39.2 Écrire `BpmnValidator` : parsing Flowable, liste blanche des éléments, refus de `scriptTask` et des `expression` et `class` arbitraires, champs référencés existants
- [ ] T39.3 Restreindre le contexte d'expressions de Flowable (aucun accès aux beans en dehors de la liste blanche)
- [ ] T39.4 Écrire les tests : script task → 422, delegate inconnu → 422, champ inexistant → 422

### Itération 27 : Éditeur de workflows

**US-40 — Dessiner mes propres circuits** · L
> En tant qu'**administrateur fonctionnel**, je veux dessiner un circuit de validation dans un éditeur graphique afin de reproduire nos processus internes.

Critères d'acceptation :
- Modeleur bpmn-js embarqué, sans CDN, avec un panneau de propriétés GED : groupes Keycloak, délais, conditions sur les champs (avec auto-complétion).
- Cycle brouillon → publication, avec nouvelle version. Les instances en cours restent sur leur version.
- Vue graphique d'une instance, avec l'étape courante surlignée.

Tâches :
- [ ] T40.1 Intégrer bpmn-js + `bpmn-js-properties-panel` (embarqués) dans `ged-web`
- [ ] T40.2 Écrire le *provider* de propriétés GED : groupes Keycloak, délais, conditions sur les champs (auto-complétion `/schema`)
- [ ] T40.3 Restreindre la palette aux éléments autorisés
- [ ] T40.4 Écrire le cycle brouillon → validation serveur → publication (`/workflow-definitions`)
- [ ] T40.5 Écrire la vue d'instance : diagramme en lecture seule avec l'étape courante surlignée

---

## H. Conformité

### Itération 28 : Rétention et corbeille

**US-41 — Appliquer automatiquement les durées de conservation** · L
> En tant que **responsable conformité**, je veux définir une durée de conservation par classe afin que les documents soient supprimés ou archivés à l'échéance légale.

Critères d'acceptation :
- `retention_policy` par classe : durée, point de départ (date de dépôt ou valeur d'un champ date), action (supprimer ou archiver).
- Un job planifié (ShedLock) applique les politiques, avec un rapport.

Tâches :
- [ ] T41.1 Migration : table `retention_policy` (classe, durée, point de départ, action)
- [ ] T41.2 Écrire le CRUD `/retention-policies` (rôle conformité)
- [ ] T41.3 Écrire `RetentionJob` (`@Scheduled` + ShedLock), traité par lots, avec rapport
- [ ] T41.4 Écrire les tests avec une horloge simulée (`Clock`)

**US-42 — Bloquer les suppressions en cas de litige** · M
> En tant que **responsable conformité**, je veux placer des documents sous legal hold afin qu'aucune suppression ne soit possible pendant un litige.

Critères d'acceptation :
- Pose et levée d'un legal hold sur des documents, des dossiers ou le résultat d'une recherche.
- Toute suppression d'un document sous hold est refusée, même pour un administrateur et même par la rétention.

Tâches :
- [ ] T42.1 Migration : tables `legal_hold` et `legal_hold_document`
- [ ] T42.2 Écrire `/legal-holds` : créer, ajouter des documents (liste, dossier, requête de recherche), lever
- [ ] T42.3 Vérifier le hold dans **toutes** les voies de suppression (API, rétention, corbeille)
- [ ] T42.4 Écrire les tests : un administrateur ne peut pas supprimer, et la rétention saute le document

**US-43 — Restaurer un document supprimé par erreur** · S
> En tant qu'**utilisateur**, je veux une corbeille afin de récupérer un document supprimé par erreur.

Tâches :
- [ ] T43.1 Mettre en place la suppression logique (`deleted_at`) et l'exclusion des listes et de la recherche
- [ ] T43.2 Écrire `/trash` : lister, restaurer, purger (droit DELETE), avec purge automatique après N jours
- [ ] T43.3 Écrire l'écran corbeille dans le frontend

### Itération 29 : Archivage et preuve

**US-44 — Archiver de façon inaltérable** · M
> En tant que **responsable conformité**, je veux que les documents archivés soient techniquement impossibles à modifier ou supprimer afin d'avoir une valeur probante.

Critères d'acceptation :
- L'archivage applique S3 Object Lock (WORM) jusqu'à la fin de la rétention.
- Une tentative de suppression directe dans S3 échoue.

Tâches :
- [ ] T44.1 Créer le bucket `ged-archive` avec Object Lock activé (mode COMPLIANCE ou GOVERNANCE, à choisir dans un ADR)
- [ ] T44.2 Écrire `ArchiveService` : copie avec `RetainUntilDate` = fin de rétention, mise à jour du statut
- [ ] T44.3 Écrire le test : `DeleteObject` sur un objet verrouillé → refus S3

**US-45 — Exporter et vérifier l'audit** · M
> En tant que **responsable conformité**, je veux exporter le journal d'audit et prouver son intégrité afin de le remettre à un auditeur.

Critères d'acceptation :
- `GET /audit` exporte par période, utilisateur ou document, en CSV ou JSON.
- La vérification de la chaîne de hash renvoie un résultat signé.
- Les partitions anciennes sont archivées vers S3 et restent vérifiables.

Tâches :
- [ ] T45.1 Écrire `GET /audit` : filtres période, utilisateur, document, formats CSV et JSON, en flux
- [ ] T45.2 Écrire `POST /audit/verify`, qui renvoie un rapport de vérification signé (clé interne)
- [ ] T45.3 Écrire l'archivage des partitions froides vers S3 (Parquet ou JSONL compressé), avec empreinte
- [ ] T45.4 Écrire l'écran de consultation de l'audit dans le frontend

---

## I. Import massif

### Itération 30 : Import simple

**US-46 — Importer un lot de documents** · L
> En tant qu'**administrateur fonctionnel**, je veux importer un lot de documents décrit par un fichier CSV afin de charger de gros volumes sans dépôt manuel.

Critères d'acceptation :
- Nouveau module `ged-importer` : Job **Spring Batch**, avec un manifeste CSV (fichier, classe, métadonnées, dossier).
- Traitement par **chunks** de 500 lignes : chaque chunk forme une transaction.
- **Reprise** après un arrêt brutal, sans doublon : `JobRepository` dans PostgreSQL et clé d'idempotence par ligne.
- Une ligne en erreur est sautée (`skipLimit`) et listée dans le rapport.
- `POST /imports` lance l'import, `GET /imports/{id}` suit son avancement.

Tâches :
- [ ] T46.1 Créer le module `ged-importer` (Spring Boot + Spring Batch, `JobRepository` dans PostgreSQL)
- [ ] T46.2 Définir le format du manifeste CSV (documenté dans `docs/import-format.md`)
- [ ] T46.3 Écrire le `Job` `importJob` : `FlatFileItemReader` → `ImportItemProcessor` → `ImportItemWriter`, en chunks de 500
- [ ] T46.4 Processor : vérification du fichier, SHA-256, `MetadataValidator`, résolution du dossier
- [ ] T46.5 Writer : JDBC batch (documents, versions), audit, outbox
- [ ] T46.6 Garantir l'idempotence par ligne (clé `import_id + n° de ligne`), avec `skipLimit` et `SkipListener` vers le rapport
- [ ] T46.7 Écrire `POST /imports` (lance un Job OpenShift ou un lancement local en dev) et `GET /imports/{id}`
- [ ] T46.8 Écrire le test : 1 000 lignes, arrêt au milieu, redémarrage → aucun doublon

### Itération 31 : Import de millions de documents

**US-47 — Importer des millions de documents sans gêner les utilisateurs** · L
> En tant qu'**exploitant**, je veux importer des millions de documents en parallèle sans ralentir les utilisateurs afin de charger un stock existant.

Critères d'acceptation :
- Partitionnement Spring Batch sur plusieurs pods, avec manifeste CSV ou JSONL.
- Copie serveur à serveur du bucket d'import vers le bucket GED, ou lecture d'un partage NFS/SMB monté.
- Les événements passent par des **topics basse priorité**. Les dépôts des utilisateurs restent rapides pendant l'import.
- Réglages de chargement initial OpenSearch (`_bulk`, rafraîchissement désactivé, 0 réplique), rétablis à la fin.
- OCR évité si le texte existe, IA évitée si les métadonnées sont fournies, rendition à la demande.

Tâches :
- [ ] T47.1 Ajouter le lecteur JSONL (`JsonItemReader`)
- [ ] T47.2 Écrire le `Partitioner` (découpage du manifeste en plages) et le step partitionné sur plusieurs pods
- [ ] T47.3 Écrire la copie serveur à serveur (`CopyObject`) depuis le bucket d'import, et le montage NFS/SMB facultatif
- [ ] T47.4 Publier sur les topics `*.bulk` basse priorité, avec des consommateurs dédiés scalés séparément
- [ ] T47.5 Écrire `BulkIndexingMode` : `refresh_interval -1`, 0 réplique, `_bulk`, rétablissement en fin de job
- [ ] T47.6 Éviter les traitements inutiles : OCR si texte fourni ou PDF texte, IA si métadonnées fournies, rendition à la demande
- [ ] T47.7 Ajouter le Job Helm `ged-importer` (template paramétrable)
- [ ] T47.8 Écrire le test de recette : 100 000 documents, en mesurant la latence des dépôts utilisateurs pendant l'import

**US-48 — Valider un import avant de le lancer** · M
> En tant qu'**administrateur fonctionnel**, je veux une validation à blanc du manifeste afin de corriger les erreurs avant l'import réel.

Critères d'acceptation :
- Le mode validation à blanc vérifie les fichiers présents, les classes existantes et les métadonnées conformes, **sans rien écrire**.
- Le rapport est téléchargeable. Une relance limitée aux lignes en erreur est possible.
- Écran de suivi des imports dans l'administration.

Tâches :
- [ ] T48.1 Ajouter le paramètre `dryRun` : processor complet, writer neutralisé
- [ ] T48.2 Écrire le rapport téléchargeable (CSV des erreurs par ligne) et la relance limitée aux lignes en erreur
- [ ] T48.3 Écrire l'écran de suivi des imports : lancement, avancement, rapport

---

## J. Intégration et durcissement

### Itération 32 : Robustesse de l'API

**US-49 — Rejouer un appel sans créer de doublon** · M
> En tant qu'**intégrateur**, je veux pouvoir relancer un appel après une coupure réseau sans créer de doublon afin que mon intégration soit fiable.

Critères d'acceptation :
- L'en-tête `Idempotency-Key` sur les créations fait qu'un même appel rejoué renvoie la même réponse.
- `ETag` et `If-Match` sur les modifications : `412` si le document a changé entre-temps.

Tâches :
- [ ] T49.1 Migration : table `idempotency_key` (clé, client, hash de la requête, réponse, expiration)
- [ ] T49.2 Écrire un filtre ou intercepteur `Idempotency-Key` sur les POST de création (réponse rejouée, 422 si le corps diffère)
- [ ] T49.3 Générer `ETag` à partir de la version d'entité (`@Version`) et vérifier `If-Match` → 412
- [ ] T49.4 Mettre à jour `ged-v1.yaml` (en-têtes) et écrire les tests

**US-50 — Protéger la GED des appels excessifs** · S
> En tant qu'**exploitant**, je veux limiter le nombre d'appels par application afin qu'une intégration défaillante ne bloque pas les utilisateurs.

Critères d'acceptation : quota par client (Bucket4j), `429` avec `Retry-After`.

Tâches :
- [ ] T50.1 Intégrer Bucket4j (stockage PostgreSQL ou cache local, à décider) avec une limite par `client_id`
- [ ] T50.2 Renvoyer `429` + `Retry-After`, avec des quotas configurables par client
- [ ] T50.3 Écrire le test : dépassement → 429

**US-51 — Être notifié des événements de la GED** · M
> En tant qu'**intégrateur**, je veux m'abonner aux événements (document créé, classé, workflow terminé) afin que mon ERP réagisse sans interroger la GED en boucle.

Critères d'acceptation :
- CRUD `/webhooks`, vers des URLs **internes uniquement** (liste blanche).
- Requêtes signées par HMAC, avec nouvelles tentatives à délai croissant et historique des livraisons.

Tâches :
- [ ] T51.1 Migration : tables `webhook` et `webhook_delivery`
- [ ] T51.2 Écrire le CRUD `/webhooks`, avec la liste blanche des domaines et réseaux internes
- [ ] T51.3 Écrire `WebhookDispatcher` (consommateur Kafka) : signature HMAC-SHA256, nouvelles tentatives à délai croissant, historique
- [ ] T51.4 Écrire `GET /webhooks/{id}/deliveries` et le rejeu manuel
- [ ] T51.5 Écrire le test avec un récepteur WireMock : vérification de la signature

### Itération 33 : Performance

**US-52 — Garantir les performances à grande échelle** · L
> En tant qu'**exploitant**, je veux mesurer les performances sur un volume réaliste afin de dimensionner la plateforme avant la mise en production.

Critères d'acceptation :
- Scénarios Gatling : ingestion de 100 000 documents et recherche concurrente, avec un objectif de **p95 < 500 ms**.
- Débits mesurés : OCR (pages par heure et par cœur), rendition, Gemma 4 (documents par heure).
- Recommandations de dimensionnement (pods, HPA, shards), et décision sur le passage à vLLM.
- Tableaux de bord Grafana (lag Kafka, files, latences) sur la supervision interne.

Tâches :
- [ ] T52.1 Écrire les scénarios Gatling (module `ged-perf`) : ingestion et recherche concurrente
- [ ] T52.2 Générer un jeu de 100 000 documents synthétiques (PDF texte, scans, Office)
- [ ] T52.3 Mesurer les débits OCR, rendition et Gemma 4, et documenter les résultats
- [ ] T52.4 Exposer les métriques Micrometer (lag Kafka, files, latences) et créer les tableaux de bord Grafana
- [ ] T52.5 Rédiger `docs/exploitation/dimensionnement.md` (pods, HPA, shards, recommandation vLLM)
