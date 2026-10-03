# CLAUDE.md — GED d'entreprise

Ce fichier guide Claude Code et les développeurs qui travaillent dans ce dépôt.

## Le projet

Une GED (gestion électronique des documents) pour les entreprises. Première cible : une **institution d'assurances sociales en Suisse**, avec **500 utilisateurs** et **50 millions de documents**.

- **Données sensibles** (santé, assurances sociales) : nLPD et secret LPGA. Il faut chiffrer au repos et en transit, **journaliser toutes les consultations** et n'écrire aucune donnée personnelle dans les logs.
- **Conservation de 20 ans** par défaut.
- **Reprise après sinistre en 6 h** (RTO) : réplication vers un second site en Suisse.
- **Navigateurs** : Chrome, Edge et Firefox.
- **Langues : français, allemand, anglais.** L'interface et les libellés existent dans les trois langues, et la recherche gère le contenu dans les trois langues.

- **Une instance par entreprise cliente**, sans multi-tenant : pas de `tenant_id`.
- **Tout reste sur place**, pour raisons légales. Aucun document, aucun texte extrait et aucune métadonnée ne sort vers un service cloud, IA comprise.
- **Champs d'indexation configurables** par les administrateurs de chaque entreprise, à chaud et sans redéploiement. Aucun champ métier n'est codé en dur.
- **Périmètre V1** :
  - socle documentaire, recherche à facettes, OCR ;
  - prévisualisation PDF/Word ;
  - classification IA locale ;
  - workflows BPMN configurables ;
  - conformité et archivage ;
  - API REST publique ;
  - import massif.
- **Hors V1** : la reprise de l'ancienne GED maison. L'import massif générique servira de base le jour où on la fera.

État actuel : les itérations validées sont commitées sur `main` (un commit par itération, voir `git log`) ; le backlog coche les tâches terminées dans [docs/stories.md](docs/stories.md). Le travail avance itération par itération (voir « Développement itératif »).

### Documentation de référence

| Document | Contenu | Identifiants |
|---|---|---|
| [docs/business-requirements.md](docs/business-requirements.md) | pourquoi : objectifs, périmètre, exigences métier, contraintes, risques | `BO-x`, `BR-xx`, `C-xx` |
| [docs/user-requirements.md](docs/user-requirements.md) | quoi, pour qui : exigences par profil, qualité perçue, parcours | `UR-xx`, `UQ-xx` |
| [docs/technical-specifications.md](docs/technical-specifications.md) | comment : architecture, données, sécurité, pipeline, API, exigences non fonctionnelles, ADR | `AP-x`, `NFR-xx`, `TQ-x` |
| [docs/stories.md](docs/stories.md) | backlog : itérations, user stories, tâches | `US-xx`, `Txx.n` |
| [docs/guides/openapi-swagger.md](docs/guides/openapi-swagger.md) | **guide** : comment fonctionnent le contrat OpenAPI, le code généré et Swagger UI ; marche à suivre pour modifier l'API | — |
| [docs/conventions.md](docs/conventions.md) | **règles de code** : packages, injection, tests, Git, build. **À lire avant d'écrire du code.** | — |

Traçabilité : `BR` → `UR` → `US` → `T`. Quand une itération change une décision, mettre à jour les spécifications techniques (et créer un ADR dans `docs/adr/` si la décision est structurante).

## Stack

| Domaine | Choix |
|---|---|
| Backend | **Java 25** (LTS), **Spring Boot 4.1**, Maven multi-module (monolithe modulaire), package `ch.louhan.ged` |
| Base de données | PostgreSQL 18, Flyway, métadonnées en `JSONB` |
| Binaires | Stockage objet S3 **sur site** : ODF/NooBaa sur OpenShift, MinIO en dev |
| Recherche | OpenSearch, index derrière un alias |
| Messagerie | Kafka (AMQ Streams), pattern outbox |
| Extraction / OCR | Apache Tika + Tesseract (`fra`, `deu`, `eng`), ClamAV |
| Prévisualisation | Gotenberg (LibreOffice headless) → PDF, `qpdf`, PDF.js |
| Workflows | Flowable (embarqué) + modeleur bpmn-js |
| IA | Ollama + **Gemma 4** interne via Spring AI, derrière `DocumentClassifier` |
| Import massif | Spring Batch (`ged-importer`, Job OpenShift) |
| API | OpenAPI 3.0.3 (ADR 0001), contrat d'abord, `openapi-generator`, Swagger UI (springdoc), lint Spectral |
| Auth | Keycloak (OIDC), JWT, comptes de service *client credentials* |
| Frontend | React + TypeScript + Vite, TanStack Query, PDF.js, bpmn-js |
| Déploiement | OpenShift **sur site**, Helm. docker-compose en dev |
| Tests | JUnit 5, Testcontainers, Gatling |

## Architecture

```
               ┌──────────── tout sur site (OpenShift on-prem) ────────────┐
[React SPA] ─┐ │                                                           │
[Applis ERP, ├─┼─ /api/v1 (OIDC / client credentials) ──> [Keycloak]       │
 scanners…]  ┘ │        │                                                  │
               │        ▼                                                  │
               │ [ged-api Spring Boot + Flowable] ──> PostgreSQL           │
               │        │    │                   └──> S3 (ODF / MinIO)     │
               │        │    └─ outbox → Kafka ──▶ [ged-worker]            │
               │        │                 ▲         ├ ClamAV               │
               │        ▼                 │         ├ Tika + Tesseract     │
               │   OpenSearch ◀── index ──┼─────────├ rendition (Gotenberg)│
               │                          │         └ IA ──> Ollama/Gemma 4│
               │ [ged-importer Spring Batch] (Job : manifeste → S3 + DB)   │
               └───────────────────────────────────────────────────────────┘
```

`ged-api`, `ged-worker` et `ged-importer` sont des déploiements séparés, scalés indépendamment. Les workers sont mis à l'échelle par HPA selon le retard (lag) Kafka.

Le pipeline de chaque nouvelle version d'un document :
1. antivirus ;
2. extraction du texte et OCR ;
3. rendition PDF et miniatures ;
4. indexation ;
5. IA, en asynchrone.

Le document est **consultable et cherchable avant d'être classé** par l'IA.

## Structure du dépôt (cible)

```
ged/
├─ pom.xml           # parent Maven (Spring Boot BOM)
├─ ged-core/         # domaine, JPA, services, ports (Storage, Search, Classifier), moteur de schéma,
│                    # workflows par défaut (src/main/resources/workflows/*.bpmn)
├─ ged-api/          # contrat OpenAPI (src/main/resources/openapi/ged-v1.yaml), REST, sécurité, Flowable
├─ ged-worker/       # consommateurs Kafka : antivirus, Tika/OCR, rendition, IA, indexation
├─ ged-importer/     # Spring Batch : import massif
├─ ged-web/          # SPA React (client API généré, bpmn-js, PDF.js, sans CDN)
├─ deploy/
│  ├─ docker-compose.yml        # postgres, minio, opensearch, kafka, keycloak, clamav, ollama, gotenberg
│  ├─ keycloak/realm-ged.json   # realm de dev
│  └─ helm/ged/                 # chart OpenShift (Deployments, Routes, HPA, NetworkPolicy…)
└─ docs/adr/         # décisions d'architecture
```

## Contrainte légale : tout reste sur place

- **Aucun appel sortant.** Une *NetworkPolicy* de sortie refuse tout, sauf les services internes. Tout code qui appelle un service externe est un bug.
- **IA** : uniquement l'Ollama interne.
  - Configuration : `ged.ai.base-url`, `ged.ai.model` (défaut `gemma4`), `ged.ai.enabled`.
  - Pas d'API LLM cloud (OpenAI, Anthropic, etc.).
  - Si le débit ne suffit pas, on passe à vLLM sur site, sans toucher à `DocumentClassifier`.
- **Frontend sans aucun CDN** : polices, PDF.js et son worker, bpmn-js, Swagger UI/Redoc sont tous embarqués dans le bundle.
- **Build** :
  - images de conteneurs depuis le **registre interne** ;
  - dépendances Maven/npm depuis un **miroir interne** (Nexus/Artifactory) ;
  - signatures ClamAV depuis un **miroir interne**.
- **Services internes** : SMTP, supervision (Prometheus/Grafana d'OpenShift), logs (Loki/EFK), sauvegardes vers un second site interne.

## Champs d'indexation configurables

C'est le cœur fonctionnel : formulaires, recherche, IA, workflows et rétention en dépendent.

**Classes de documents** (`document_class`)
- Hiérarchiques, avec héritage des champs.
- Chaque classe porte sa rétention, son workflow par défaut et son filigrane éventuel.

**Champs** (`field_definition`, rattachés par `class_field`)
- Types disponibles :
  - texte court ou long
  - entier, décimal, montant avec devise
  - date, date-heure
  - booléen
  - liste de valeurs (`value_list`, éventuellement hiérarchique)
  - utilisateur ou groupe
  - lien vers un autre document
- Propriétés :
  - code immuable, libellés fr/de/en
  - obligatoire, multi-valué, unique, défaut, regex, min/max
  - recherchable, filtrable, facette, triable, colonne de résultats
  - consigne d'extraction IA

**Stockage et validation**
- Les valeurs vont dans `document.metadata JSONB`, avec un index GIN `jsonb_path_ops`.
- Elles sont validées par `MetadataValidator`, à partir des définitions mises en cache (Caffeine, invalidé par l'événement Kafka `schema.changed`).

**OpenSearch**
- Chaque champ a un mapping typé sous `meta.<code>`, ajouté par `PUT _mapping` additif.
- Le mapping est `dynamic: strict`.
- Un changement cassant déclenche une réindexation vers un nouvel index, puis la bascule de l'alias, avec un rapport des documents non conformes.

**Versionnage**
- Chaque modification crée une `schema_revision`, tracée dans l'audit.
- Le schéma s'exporte et s'importe en **YAML**, avec les `.bpmn`.

**Consommateurs du schéma**
- Le frontend génère formulaires, facettes et colonnes à partir de `GET /api/v1/schema`.
- L'IA construit son schéma de sortie JSON à partir des champs de la classe.

## Prévisualisation (PDF, Word, Office)

- **Principe** : toute version a une **rendition PDF**, affichée par un seul visualiseur, PDF.js.
  - **PDF** : affiché tel quel.
  - **Word, Excel, PowerPoint, ODF, RTF** : convertis par **Gotenberg** (LibreOffice headless, déployé en interne).
  - **Emails** : passent par Tika, puis HTML, puis PDF.
  - **TIFF** : converti en PDF.
  - **Formats non pris en charge** : fiche avec miniature et bouton de téléchargement.
- **Stockage** : la rendition est linéarisée par `qpdf` et stockée sous `renditions/<sha256>.pdf`. Les miniatures PNG sont générées à partir de la première page.
- **Moment de la génération** :
  - à l'ingestion d'une nouvelle version ;
  - **à la demande, puis mise en cache** pour les documents arrivés par import massif.
- **Le fichier original n'est jamais modifié.** Le téléchargement renvoie toujours l'original.
- **Affichage** : chargement progressif via les **requêtes HTTP Range** sur une URL présignée de courte durée, délivrée après vérification de l'ACL READ. Chaque consultation est auditée.
- **Confort** : surlignage des termes recherchés, et filigrane facultatif par classe (utilisateur, date, « Confidentiel »).
- **API** : `GET /documents/{id}/preview` renvoie l'URL, le nombre de pages et le statut (`READY`, `PENDING` ou `UNSUPPORTED`).

## Corbeilles de travail

Une **corbeille** est la liste des documents qu'une personne ou un service **doit traiter**. C'est le point d'entrée quotidien des utilisateurs. À ne pas confondre avec les **éléments supprimés**, la corbeille au sens « poubelle ».

- **Corbeille personnelle** pour chaque utilisateur, créée à sa première connexion. **Corbeilles de groupe** : chaque groupe ou département Keycloak peut en avoir plusieurs, configurées par l'administrateur (ex. « AI — Courrier entrant », « AI — Recours »).
- **Un document peut être dans plusieurs corbeilles** : pour action ou en copie pour information. Il n'est actif qu'une fois par corbeille.
- **Arrivée** dans une corbeille :
  - tri du courrier entrant par le service courrier ;
  - **règles d'attribution automatiques** (classe + conditions sur les champs, y compris après une proposition de l'IA) ;
  - transmission manuelle ;
  - **étape de workflow**.
- **Actions** : déposer, prendre (corbeille de groupe), transmettre, copier pour information, marquer traité. Une fois traité, le document **quitte la corbeille** et reste classé dans son dossier.
- **Coexistence** : les **dossiers** servent au classement définitif et aux droits, les **corbeilles** au travail en cours.
- **Une corbeille ne donne aucun droit.** Les droits du dossier priment :
  - on ne peut déposer un document que chez un destinataire qui a déjà READ, sinon `422` ;
  - une règle qui vise un destinataire sans droit ne fait rien et produit une alerte ;
  - les listes de corbeille sont filtrées par ACL **dans la requête**.
- **Workflows** : chaque tâche Flowable est un **élément de corbeille** (source `WORKFLOW`). Il n'y a pas de « boîte de tâches » séparée : **ma corbeille = tout mon travail**.
- **Modèle** : `basket` (USER | GROUP), `basket_item` (ACTION | INFO, TODO | IN_PROGRESS | DONE, source MAIL_SORTING | RULE | MANUAL | WORKFLOW), `routing_rule`.
- **API** : `/baskets`, `/baskets/{id}/items`, `/basket-items/{id}/claim|transfer|copy|done`, `/routing-rules`.

## Workflows de validation

- **Éditeur** : modeleur **bpmn-js** dans l'administration.
  - Un *properties panel* propre à la GED permet de choisir les groupes Keycloak, les délais et escalades, et les conditions sur les champs configurés (par exemple `metadata.montant > 5000`).
- **Exécution** : **Flowable**, embarqué dans `ged-api`.
- **Palette restreinte de delegates GED** :
  - changer le statut, verrouiller ou déverrouiller ;
  - mettre à jour un champ ;
  - envoyer une notification ;
  - archiver ;
  - lancer un sous-processus.
- **Script tasks et expressions arbitraires interdites.** Le BPMN est validé côté serveur avant tout déploiement : parsing, liste blanche, existence des champs référencés.
- **Cycle de vie** : brouillon, puis publication, ce qui crée une nouvelle version. Les instances en cours restent sur leur version d'origine.
- **Affectation** : un circuit s'affecte à une classe de documents, en déclenchement automatique ou manuel.
- **Exécution côté utilisateur** :
  - les tâches arrivent dans la **corbeille** du valideur ou du groupe : approuver, rejeter avec commentaire, déléguer ;
  - timers d'escalade ;
  - vue graphique de l'instance ;
  - un `audit_event` par transition.

**6 workflows par défaut**, livrés dans `ged-core/src/main/resources/workflows/`. Ils sont protégés et duplicables, et leurs paramètres se règlent à l'affectation :

| Modèle | Circuit |
|---|---|
| Validation simple | 1 valideur |
| Double validation | 2 valideurs successifs |
| Validation selon un seuil | 2e valideur si `champ > seuil` |
| Validation collégiale | plusieurs valideurs en parallèle, avec une règle de quorum |
| Relecture et publication | rédaction → relecture → approbation → publication et verrouillage |
| Prise de connaissance | chacun confirme « lu », avec relance automatique |

## API REST publique

- **Usage** : elle couvre toute la GED. Le frontend n'a **aucun accès privilégié** et passe par la même API que les applications tierces.
- **Contrat d'abord** :
  - `ged-api/src/main/resources/openapi/ged-v1.yaml` est la source de vérité ;
  - interfaces Spring et client TypeScript générés à partir de ce fichier ;
  - on ne modifie jamais le code généré à la main.
- **Conventions** :
  - `/api/v1/...`, sans changement cassant dans une version publiée ;
  - erreurs au format RFC 9457 (`application/problem+json`), avec codes métier stables ;
  - pagination par curseur ;
  - concurrence optimiste par `ETag` et `If-Match` (`412` en cas de conflit) ;
  - en-tête `Idempotency-Key` sur les créations.
- **Authentification** :
  - utilisateurs : JWT OIDC ;
  - applications : comptes de service Keycloak (*client credentials*), avec les scopes `documents:read`, `documents:write`, `schema:admin`, `workflow:act`… ;
  - ACL identiques pour les deux ;
  - quota par client (Bucket4j) ;
  - chaque appel est audité.
- **Domaines** :
  - schéma, avec `GET /classes/{id}/json-schema` qui donne le schéma exact des métadonnées d'une classe ;
  - dossiers, documents, versions ;
  - **corbeilles** (`/baskets`, `/basket-items`) et règles d'attribution (`/routing-rules`) ;
  - fichiers : upload multipart présigné, envoi direct en flux jusqu'à 100 Mo, téléchargement par redirection 302 vers une URL présignée, miniature, texte, prévisualisation ;
  - recherche (`POST /search`) ;
  - workflows et tâches ;
  - revue IA ;
  - conformité ;
  - imports ;
  - webhooks signés par HMAC, vers des URLs **internes uniquement**.
- **Documentation** : Swagger UI et Redoc servis par la GED sur `/api/docs`.

## Import massif

- **Spring Batch** dans `ged-importer`, exécuté comme un Job OpenShift.
  - Traitement par lots, avec reprise après panne.
  - Partitions en parallèle.
  - Une ligne en erreur est sautée, dans la limite d'un seuil.
- **Entrée** : un manifeste CSV ou JSONL (fichier, classe, métadonnées, dossier, ACL), avec les fichiers dans un bucket d'import S3 ou sur un partage NFS/SMB monté.
- **Étapes** :
  1. validation à blanc facultative ;
  2. copie serveur à serveur vers S3, SHA-256 et déduplication ;
  3. `MetadataValidator`, puis insertion en JDBC batch ;
  4. outbox vers des **topics basse priorité**, pour que les dépôts des utilisateurs restent prioritaires ;
  5. rapport final, avec possibilité de relancer uniquement les lignes en erreur.
- **Chargement initial** :
  - OpenSearch en `_bulk`, avec `refresh_interval: -1` et 0 réplique pendant le chargement ;
  - OCR évité si le texte existe déjà ;
  - IA évitée si les métadonnées sont fournies ;
  - rendition à la demande.

## Modèle de données principal

- `folder` : chemin `ltree` et ACL héritée
- `document_class`, `field_definition`, `class_field`, `value_list`, `value_list_item`, `schema_revision`
- `document` : dossier, classe, titre, statut, `metadata JSONB`, version courante, rétention, legal hold
- `document_version` : clé S3, sha256, taille, mime, auteur, statuts OCR/rendition/IA
- `tag`, `document_tag`
- `acl_entry` : principal (utilisateur ou groupe), ressource, permission READ/WRITE/DELETE/ADMIN
- `retention_policy`, `legal_hold`, `legal_hold_document`
- `audit_event` : append-only, partitionné par mois, avec chaînage de hash
- `outbox_event`
- tables Flowable

## Règles à respecter

- **Aucun binaire** n'est stocké en base, ni gardé en mémoire ou sur disque par l'API.
  - Les uploads passent par l'upload présigné, ou par l'envoi en flux plafonné.
  - Les objets sont adressés par leur SHA-256.
- **Pagination par keyset ou curseur**, jamais `OFFSET`.
- **Droits dans la recherche** : les principaux autorisés sont indexés dans OpenSearch et filtrés dans la requête. On ne filtre jamais les résultats après coup.
- **Une corbeille ne donne aucun droit** : un dépôt dans une corbeille exige que le destinataire ait déjà READ, et les listes de corbeille sont filtrées par ACL dans la requête.
- **Cohérence entre la base et Kafka** : on passe toujours par l'outbox. Les erreurs vont en DLQ, avec rejeu possible.
- **Audit** : toute action métier, d'administration, de schéma, de workflow ou de consultation produit un `audit_event`, jamais modifié ni supprimé.
- **Conformité** :
  - un legal hold bloque toute suppression ;
  - l'archivage passe par S3 Object Lock ;
  - les jobs planifiés tournent sous ShedLock.
- **IA** :
  - toujours derrière `DocumentClassifier`, désactivable ;
  - asynchrone et non bloquante ;
  - en dessous du seuil de confiance, le document part en revue humaine.
- **Migrations** : uniquement par Flyway, en ajout uniquement.
- **OpenSearch** : accès toujours via l'alias. Shards dimensionnés pour environ 10 M de documents par index.
- **Aucun appel sortant et aucun CDN** (voir « Contrainte légale »).

## Développement itératif (à respecter)

Le product owner doit **comprendre le code produit**. On avance donc par **petites itérations verticales** : chaque itération traverse toutes les couches (base, service, API, test) sur une seule fonctionnalité. Le backlog complet, soit 36 itérations et 57 user stories, est dans [docs/stories.md](docs/stories.md).

Rituel de chaque itération :
1. **Annonce** : stories visées, notion nouvelle, fichiers touchés.
2. **Code** : 200 à 500 lignes au maximum, **une seule nouvelle technologie** à la fois.
3. **Démo** : une commande ou un écran pour voir le résultat.
4. **Explication** : visite guidée du code, choix faits, alternatives écartées.
5. **Arrêt** : attendre la validation du product owner avant l'itération suivante.
6. **Commit** : après validation, et seulement à ce moment-là, un commit par itération. Message : `Itération N : <titre> (US-xx)`.

### Les blocs

| Bloc | Itérations | Contenu |
|---|---|---|
| **A. Fondations** | 1–5 | Hello GED, PostgreSQL + Flyway, contrat OpenAPI, Keycloak, **premier déploiement OpenShift dès l'itération 5** |
| **B. Documents et corbeilles** | 6–11 | dépôt vers MinIO, versions + empreinte SHA-256, upload présigné, droits d'accès, audit infalsifiable, **corbeilles de travail** |
| **C. Schéma configurable** | 12–15 | classes, champs, validation, listes de valeurs, export YAML, **règles d'attribution aux corbeilles** |
| **D. Premier frontend** | 16–18 | React + connexion Keycloak, formulaire d'indexation **généré** depuis le schéma, **écran « Ma corbeille » et tri du courrier entrant** |
| **E. Pipeline et recherche** | 19–26 | Kafka, OCR, OpenSearch, facettes, prévisualisation Word/PDF, antivirus |
| **F. IA** | 27 | Gemma 4 + file de revue humaine |
| **G. Workflows** | 28–30 | Flowable (tâches = éléments de corbeille), 6 workflows par défaut, éditeur bpmn-js |
| **H. Conformité** | 31–32 | rétention, éléments supprimés, legal hold, archivage verrouillé |
| **I. Import massif** | 33–34 | Spring Batch : d'abord simple, puis en parallèle sur plusieurs pods |
| **J. Durcissement** | 35–36 | robustesse de l'API, tests de charge |

Le contrat `ged-v1.yaml` et les modules Maven **grandissent au fil des itérations**. Un module n'est créé que lorsqu'on en a besoin : `ged-worker` arrive avec Kafka (itération 19) et `ged-importer` avec Spring Batch (itération 33). Le docker-compose et le chart Helm s'enrichissent de la même façon, un service à la fois.

### Jalons et blocs : quel lien ?

- Les **jalons** (section suivante) décrivent **ce qu'il faut livrer**, découpé par domaine fonctionnel. C'est la **cible** de la V1.
- Les **blocs** et leurs **itérations** décrivent **dans quel ordre on le construit**, en petits morceaux compréhensibles.
- **C'est l'itération qu'on valide**, une par une. Un jalon est atteint quand toutes les itérations qui le réalisent sont validées.

Livrer tous les blocs revient à atteindre tous les jalons, mais le découpage n'est pas un pour un :

| Jalon (cible) | Réalisé dans |
|---|---|
| **0. Contrat d'API** | **réparti** : démarré à l'itération 3, puis enrichi à chaque itération qui touche l'API |
| **1. Squelette et infrastructure** | **bloc A** (itérations 1, 2, 4, 5) |
| **2. Moteur de schéma** | **bloc C** (12–14), plus les règles d'attribution (15) |
| **3. Socle documentaire** | **bloc B** (6–10), plus les **corbeilles** (11) |
| **4. Pipeline d'ingestion et import massif** | **bloc E** (19, 20, 25, 26) pour le pipeline, et **bloc I** (33–34) pour l'import Spring Batch |
| **5. Recherche** | **bloc E** (21–24) |
| **6. Classification IA** | **bloc F** (27) |
| **7. Workflows** | **bloc G** (28–30) |
| **8. Conformité** | **bloc H** (31–32) |
| **9. Frontend** | **réparti** : bloc D (16–18) pour la base et les corbeilles, puis un écran ajouté dans chaque bloc suivant (recherche en 24, visionneuse en 25, revue IA en 27, actions de workflow dans la corbeille en 28, modeleur en 30, conformité en 31–32, imports en 34) |
| *(pas de jalon dédié)* | **bloc J** (35–36) : robustesse de l'API et performance, issues des jalons 0 et 3 et de la section « Vérification » |

Les écarts avec l'ordre des jalons sont volontaires :
1. **Les documents passent avant le schéma** (bloc B avant C, alors que le jalon 3 suit le 2). On apprend d'abord à stocker un fichier, puis on ajoute les champs configurables par-dessus.
2. **Le contrat d'API et le frontend sont répartis** sur toutes les itérations, au lieu d'avoir un jalon chacun. On n'écrit pas 30 endpoints d'un coup, ni tous les écrans à la fin.
3. **L'import massif est séparé du pipeline et placé à la fin** (bloc I). Il a besoin que tout le reste existe : schéma, pipeline, indexation et audit.
4. **Les corbeilles** (exigence ajoutée après le démarrage) sont réparties dans trois blocs : le modèle et l'API juste après les droits et l'audit (11), les règles d'attribution après le schéma (15), les écrans avec le premier frontend (18).

## Plan de mise en place de la V1

La V1 est décrite en **10 jalons (0 à 9)**, qui forment la **cible fonctionnelle**. Chaque jalon a un objectif, des technologies, des livrables et un critère « terminé quand ».

**L'ordre de construction n'est pas celui des jalons** : c'est celui des itérations (voir « Jalons et blocs : quel lien ? »). Un même jalon peut être réalisé par plusieurs itérations, dans des blocs différents. Il est atteint quand toutes ses itérations sont validées.

### Vue d'ensemble

| # | Jalon | Technologies principales | Dépend de |
|---|---|---|---|
| 0 | Contrat d'API | OpenAPI 3.0.3, openapi-generator | — |
| 1 | Squelette et infrastructure de dev | Maven, Spring Boot 4, Docker Compose, Keycloak, Helm | 0 |
| 2 | Moteur de schéma configurable | PostgreSQL JSONB, Flyway, Caffeine, OpenSearch mapping | 1 |
| 3 | Socle documentaire et corbeilles | Spring Data JPA, S3 SDK (MinIO/ODF), Spring Security | 2 |
| 4 | Pipeline d'ingestion et import massif | Kafka, Tika, Tesseract, ClamAV, Gotenberg, qpdf, **Spring Batch** | 3 |
| 5 | Recherche | OpenSearch (requêtes, agrégations, highlight) | 4 |
| 6 | Classification IA | Spring AI, Ollama, Gemma 4 | 4 |
| 7 | Workflows de validation | Flowable, bpmn-js | 3 |
| 8 | Conformité et archivage | ShedLock, S3 Object Lock, chaîne de hash | 3 |
| 9 | Frontend | React, TypeScript, Vite, TanStack Query, PDF.js, bpmn-js | 2 → 8 |

La colonne « Dépend de » indique les **dépendances techniques** entre domaines, pas un ordre de travail. L'ordre de travail est celui des itérations. Si l'équipe grandit, ces dépendances indiquent ce qui peut être mené en parallèle : la recherche et l'IA après le pipeline, les workflows et la conformité après le socle documentaire.

### Jalon 0 : Contrat d'API

- **Objectif** : l'API REST de la V1 est décrite par un contrat unique, partagé par le backend, le frontend et les intégrateurs. Le contrat est **écrit avant le code de chaque endpoint**, mais **progressivement** : démarré à l'itération 3, enrichi à chaque itération. La liste ci-dessous décrit son état final.
- **Technologies** : OpenAPI 3.0.3, `openapi-generator-maven-plugin` (interfaces Spring) et `openapi-generator` côté TypeScript (client du frontend).
- **Livrables** :
  - `ged-api/src/main/resources/openapi/ged-v1.yaml` : tous les domaines (schéma, dossiers, documents, fichiers, prévisualisation, recherche, workflows, tâches, IA, conformité, imports, webhooks) ;
  - modèles communs :
    - erreurs `problem+json` (RFC 9457) ;
    - pagination par curseur ;
    - `ETag`/`If-Match` ;
    - `Idempotency-Key` ;
    - `metadata: object` ;
  - schémas de sécurité : OIDC pour les utilisateurs, *client credentials* et scopes pour les applications ;
  - ADR `docs/adr/0001-contract-first.md`.
- **Terminé quand** (vérifié à **chaque** itération qui touche l'API) : le YAML est valide (lint Spectral), le code serveur et le client TypeScript se génèrent sans erreur, et le contrat couvre tous les endpoints livrés.

### Jalon 1 : Squelette et infrastructure de dev

- **Objectif** : un projet qui compile, démarre et se déploie, avec toute l'infrastructure disponible en local.
- **Technologies** : Java 25, Spring Boot 4.1, Maven multi-module, Docker Compose, Keycloak, Helm, CI (pipeline interne).
- **Livrables** :
  - `pom.xml` parent et module `ged-api`. Les autres modules arrivent plus tard : `ged-web` (itération 16), `ged-core` et `ged-worker` (itération 19), `ged-importer` (itération 33) ;
  - `deploy/docker-compose.yml` avec PostgreSQL et Keycloak. Les autres services s'ajoutent quand une itération en a besoin : MinIO (6), Kafka (19), OpenSearch (21), Gotenberg (25), ClamAV (26), Ollama (27, ou pointage vers l'instance existante) ;
  - `deploy/keycloak/realm-ged.json` : realm de dev, avec utilisateurs, groupes et client `ged-web` + compte de service de test ;
  - sécurité de base : `ged-api` en resource server JWT, endpoint `/actuator/health` ;
  - base Testcontainers réutilisable pour les tests d'intégration ;
  - CI : build, tests, images vers le **registre interne**, dépendances via le **miroir interne** ;
  - `deploy/helm/ged/` minimal : Deployments, Services, Routes, probes, NetworkPolicy de sortie qui refuse tout.
- **Terminé quand** :
  - `docker compose up` puis `mvn verify` passent en local ;
  - l'API répond sur `/actuator/health` avec un jeton Keycloak ;
  - `helm install` fonctionne sur un namespace de recette.

### Jalon 2 : Moteur de schéma configurable

- **Objectif** : les administrateurs créent classes, champs et référentiels à chaud. C'est la base de tout le reste.
- **Technologies** :
  - PostgreSQL (`JSONB`, GIN `jsonb_path_ops`), Flyway ;
  - Spring Data JPA, Caffeine (cache), Kafka (événement `schema.changed`) ;
  - client Java OpenSearch (`PUT _mapping`) ;
  - Jackson YAML (export/import).
- **Livrables** :
  - migration `V1__schema.sql` : `document_class`, `field_definition`, `class_field`, `value_list`, `value_list_item`, `schema_revision` ;
  - services : CRUD classes, champs et référentiels, héritage entre classes, `schema_revision` à chaque modification ;
  - `MetadataValidator` : types, obligatoire, regex, min/max, multi-valué, listes de valeurs ;
  - génération du JSON Schema d'une classe (`GET /classes/{id}/json-schema`) ;
  - synchronisation du mapping OpenSearch :
    - `meta.<code>` typé, `dynamic: strict` ;
    - détection des changements cassants, qui déclenchent un job de réindexation avec bascule d'alias et rapport ;
  - export/import YAML du schéma ;
  - endpoints `/schema`, `/classes`, `/fields`, `/value-lists`.
- **Terminé quand** :
  - une classe « Facture » créée par l'API est validée, mappée dans OpenSearch et exportable en YAML, sans redémarrage ;
  - un changement de type déclenche la réindexation et produit son rapport.

### Jalon 3 : Socle documentaire

- **Objectif** : déposer, ranger, versionner et sécuriser des documents, et organiser le travail en **corbeilles**.
- **Technologies** :
  - Spring Data JPA, AWS SDK v2 S3 (compatible MinIO/ODF) ;
  - URL présignées, multipart upload ;
  - Spring Security (ACL), PostgreSQL `ltree`.
- **Livrables** :
  - migration : `folder`, `document`, `document_version`, `tag`, `document_tag`, `acl_entry`, `audit_event` (partitionnée par mois, chaînage de hash), `outbox_event` ;
  - dossiers en arborescence `ltree`, ACL avec héritage ;
  - documents : CRUD, métadonnées validées par le schéma, versions, tags ;
  - fichiers :
    - upload multipart présigné (init, parts, complete) ;
    - envoi direct en flux jusqu'à 100 Mo ;
    - téléchargement par 302 vers une URL présignée ;
    - stockage par SHA-256, avec déduplication ;
  - concurrence optimiste (`ETag`), `Idempotency-Key`, pagination keyset ;
  - service d'audit append-only, avec un événement par action ;
  - **corbeilles de travail** : `basket`, `basket_item`, corbeilles personnelles et de groupe, déposer/prendre/transmettre/copier/traiter, contrôle READ du destinataire, et **règles d'attribution** (`routing_rule`) une fois le schéma disponible ;
  - quotas par client (Bucket4j).
- **Terminé quand** :
  - un document de 2 Go se dépose par upload présigné ;
  - un utilisateur sans droit reçoit un `403` ;
  - chaque action apparaît dans l'audit, et la chaîne de hash se vérifie ;
  - un document transmis apparaît dans la corbeille du destinataire, et un dépôt vers un destinataire sans droit est refusé.

### Jalon 4 : Pipeline d'ingestion et import massif

- **Objectif** : traiter chaque document de façon asynchrone (antivirus, texte, OCR, prévisualisation, indexation), et importer des millions de documents.
- **Technologies** :
  - **Kafka** (AMQ Streams), pattern outbox, DLQ ;
  - **ClamAV** (antivirus, signatures depuis le miroir interne) ;
  - **Apache Tika** (extraction de texte), **Tesseract** + langues `fra`, `deu`, `eng` (OCR) ;
  - **Gotenberg / LibreOffice headless** (Word/Excel/PowerPoint → PDF), **qpdf** (linéarisation) ;
  - **Spring Batch** (import massif, module `ged-importer`, Job OpenShift) ;
  - OpenSearch `_bulk`, HPA sur le lag Kafka.
- **Livrables, côté pipeline (`ged-worker`)** :
  - relais outbox vers Kafka ;
  - consommateurs dans cet ordre :
    1. antivirus ;
    2. extraction et OCR ;
    3. **rendition PDF** et miniatures, stockées sous `renditions/<sha256>.pdf` ;
    4. indexation OpenSearch ;
  - topics prioritaires (dépôts des utilisateurs) et topics **basse priorité** (import) ;
  - DLQ et rejeu, délai maximal par conversion ;
  - endpoint `/documents/{id}/preview` (`READY`, `PENDING` ou `UNSUPPORTED`), avec génération à la demande puis cache.
- **Livrables, côté import massif (`ged-importer` avec Spring Batch)** :
  - un `Job` Spring Batch en *chunks* de 500 lignes, avec reprise après panne grâce au `JobRepository` dans PostgreSQL ;
  - lecture du manifeste CSV/JSONL (`FlatFileItemReader` / `JsonItemReader`) ;
  - partitionnement (`Partitioner`) pour le traitement en parallèle sur plusieurs pods ;
  - politique de saut des lignes en erreur (`skipLimit`) ;
  - processor :
    - existence du fichier ;
    - SHA-256 et déduplication ;
    - copie serveur à serveur du bucket d'import vers le bucket GED ;
    - `MetadataValidator` ;
  - writer : JDBC batch pour les documents, `COPY` pour l'audit, outbox basse priorité ;
  - mode **validation à blanc** ;
  - rapport final téléchargeable, avec relance limitée aux lignes en erreur ;
  - endpoints `POST /imports` et `GET /imports/{id}` ;
  - réglages de chargement initial OpenSearch (`refresh_interval: -1`, 0 réplique, puis rétablissement) ;
  - OCR évité si le texte existe déjà, IA évitée si les métadonnées sont fournies, rendition à la demande.
- **Mesures** : débit réel de l'OCR (pages par heure et par cœur) et de Gemma 4 (documents par heure) sur un échantillon de vrais documents.
- **Terminé quand** :
  - un scan déposé devient cherchable par son contenu, et un `.docx` s'affiche en prévisualisation ;
  - l'import de 100 000 documents reprend sans doublon après l'arrêt brutal du pod ;
  - les dépôts des utilisateurs restent rapides pendant l'import.

### Jalon 5 : Recherche

- **Objectif** : retrouver rapidement un document parmi des millions, avec des facettes générées à partir des champs configurés.
- **Technologies** : OpenSearch (`bool` query, agrégations pour les facettes, `highlight`, `search_after`), alias d'index.
- **Livrables** :
  - `POST /search` : plein texte, filtres sur `meta.<code>`, facettes pour les champs marqués « facette », tri, surlignage, pagination `search_after` ;
  - filtrage par ACL dans la requête, grâce aux principaux autorisés indexés ;
  - mise à jour de l'index quand les droits changent ;
  - réindexation sans interruption, avec bascule d'alias ;
  - dimensionnement des shards (environ 10 M de documents par index).
- **Terminé quand** :
  - les facettes de « Facture » apparaissent sans code spécifique ;
  - un utilisateur ne voit jamais un document auquel il n'a pas droit ;
  - Gatling mesure un p95 < 500 ms sur 100 000 documents.

### Jalon 6 : Classification IA

- **Objectif** : proposer automatiquement la classe d'un document et remplir ses champs, sans que rien ne sorte de l'entreprise.
- **Technologies** :
  - **Spring AI** (module Ollama) ;
  - **Ollama + Gemma 4** interne, avec *structured outputs* (`format` = JSON Schema) ;
  - consommateur Kafka dédié, à concurrence limitée.
- **Livrables** :
  - interface `DocumentClassifier` et implémentation `OllamaClassifier` ;
  - prompt construit à partir des classes et des consignes d'extraction de chaque champ ;
  - schéma de sortie généré dynamiquement ;
  - texte tronqué aux N premières pages, configurable ;
  - score de confiance : en dessous du seuil, le document va dans la **file de revue humaine** (`/classification-review`) ;
  - `POST /documents/{id}/reclassify` ;
  - configuration `ged.ai.enabled`, `ged.ai.base-url`, `ged.ai.model`.
- **Terminé quand** :
  - une facture scannée est pré-remplie correctement ;
  - un cas incertain part en revue ;
  - tout fonctionne avec une NetworkPolicy qui refuse toute sortie ;
  - le débit mesuré permet de décider s'il faut passer à vLLM.

### Jalon 7 : Workflows de validation

- **Objectif** : les administrateurs dessinent leurs circuits, et 6 circuits simples sont fournis par défaut.
- **Technologies** : **Flowable** (moteur BPMN embarqué, Spring Boot starter), **bpmn-js** et son properties panel (modeleur graphique), SMTP interne.
- **Livrables** :
  - Flowable embarqué dans `ged-api` ;
  - palette de *delegates* GED : statut, verrouillage, mise à jour d'un champ, notification, archivage, sous-processus ;
  - validation du BPMN côté serveur : parsing, liste blanche, **script tasks interdites**, existence des champs référencés ;
  - cycle de vie brouillon → publication → nouvelle version, et affectation à une classe de documents ;
  - **6 modèles par défaut** dans `ged-core/src/main/resources/workflows/` :
    - validation simple ;
    - double validation ;
    - validation selon un seuil ;
    - validation collégiale ;
    - relecture et publication ;
    - prise de connaissance ;
  - ces modèles sont protégés, duplicables et paramétrables à l'affectation ;
  - tâches présentées comme **éléments de corbeille** (approuver, rejeter, déléguer), timers d'escalade, notifications email, un `audit_event` par transition ;
  - endpoints `/workflow-definitions`, `/workflow-templates`, `/tasks`, `/process-instances`.
- **Terminé quand** :
  - un circuit « montant > 5 000 € » suit le bon chemin ;
  - une script task est refusée ;
  - les 6 modèles sont présents sur une installation neuve.

### Jalon 8 : Conformité et archivage

- **Objectif** : garantir la conservation légale, l'impossibilité de supprimer un document sous legal hold, et un audit infalsifiable.
- **Technologies** : jobs planifiés Spring `@Scheduled` + **ShedLock**, **S3 Object Lock** (WORM), chaînage de hash SHA-256.
- **Livrables** :
  - `retention_policy` par classe : durée, action (supprimer ou archiver), job planifié ;
  - legal hold : pose, levée, blocage de toute suppression ;
  - **éléments supprimés** (suppression logique) avec restauration et purge ;
  - archivage avec Object Lock ;
  - export de l'audit et vérification de la chaîne de hash (`GET /audit`) ;
  - partitions froides de l'audit archivées vers S3.
- **Terminé quand** :
  - un document arrivé en fin de rétention est traité automatiquement ;
  - un document sous legal hold ne peut pas être supprimé, même par un administrateur ;
  - toute altération de l'audit est détectée.

### Jalon 9 : Frontend (en parallèle à partir du jalon 2)

- **Objectif** : une interface complète, sans aucun CDN, qui ne passe que par l'API publique.
- **Technologies** :
  - React + TypeScript + Vite, TanStack Query ;
  - client API généré au jalon 0 ;
  - **PDF.js** (visionneuse), **bpmn-js** (modeleur) ;
  - authentification OIDC (Keycloak).
- **Livrables, ajoutés au fil des jalons** :
  - **après le jalon 2** : administration des classes, champs et référentiels ; formulaires d'indexation **générés** à partir de `/schema` ;
  - **après le jalon 3** : **écran d'accueil « Ma corbeille »** et corbeilles de groupe, écran de **tri du courrier entrant**, navigation dans les dossiers, upload par glisser-déposer, fiche document, versions, ACL ;
  - **après le jalon 4** : visionneuse PDF.js (chargement progressif par Range, filigrane facultatif), suivi des imports ;
  - **après le jalon 5** : recherche à facettes, surlignage des termes jusque dans la visionneuse ;
  - **après le jalon 6** : file de revue IA ;
  - **après le jalon 7** : actions de workflow dans « Ma corbeille », modeleur BPMN, vue graphique d'une instance ;
  - **après le jalon 8** : rétention, legal hold, éléments supprimés, consultation de l'audit.
- **Terminé quand** : le scénario de bout en bout (voir « Vérification ») se déroule entièrement dans l'interface, et une capture réseau ne montre aucun appel externe.

## Commandes (une fois le squelette en place)

```bash
docker compose -f deploy/docker-compose.yml up -d   # infrastructure de dev
mvn verify                                          # build + tests (Testcontainers)
mvn -pl ged-api spring-boot:run                     # API
mvn -pl ged-worker spring-boot:run                  # worker
cd ged-web && npm install && npm run dev            # frontend
helm install ged deploy/helm/ged -n <namespace>     # déploiement OpenShift
```

## Vérification

- **Bout en bout** :
  1. Créer à chaud une classe « Facture » avec ses champs.
  2. Déposer un scan, puis vérifier l'OCR, le pré-remplissage par l'IA, les facettes et la recherche par un mot du scan.
  3. Changer le type d'un champ, puis vérifier la réindexation et son rapport.
- **Prévisualisation** :
  - un PDF de 500 pages, un `.docx`, un `.doc`, un `.xlsx` et un TIFF s'affichent ;
  - la première page apparaît immédiatement ;
  - un utilisateur sans droit reçoit un `403` ;
  - un fichier corrompu part en DLQ ;
  - l'original reste intact.
- **Workflows** :
  - un circuit conditionnel selon le montant suit le bon chemin ;
  - une script task est refusée ;
  - les 6 modèles sont présents sur une installation neuve.
- **API** :
  - tests de contrat ;
  - scénario `curl` avec un compte de service ;
  - réception d'un webhook signé.
- **Import** :
  - validation à blanc d'un manifeste ;
  - 100 000 documents avec arrêt brutal du pod en cours de route, puis reprise sans doublon ;
  - les dépôts des utilisateurs restent rapides pendant l'import.
- **Souveraineté** : avec une NetworkPolicy qui refuse toute sortie, tout doit fonctionner, IA comprise, sans aucun appel externe.
- **Charge** :
  - Gatling sur 100 000 documents, avec un objectif de p95 < 500 ms en recherche ;
  - mesure du débit d'Ollama/Gemma 4.
