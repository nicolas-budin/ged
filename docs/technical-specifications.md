# Spécifications techniques — GED d'entreprise

| | |
|---|---|
| **Document** | Spécifications techniques (Technical Specifications) |
| **Version** | 0.1 — brouillon |
| **Date** | 2026-10-02 |
| **Statut** | Cible V1, affinée à chaque itération |
| **Documents liés** | [Exigences métier](business-requirements.md) · [Exigences utilisateurs](user-requirements.md) · [Backlog](stories.md) · [CLAUDE.md](../CLAUDE.md) |

Ce document décrit la **solution cible** de la V1. Elle est construite **progressivement**, itération par itération (voir [CLAUDE.md](../CLAUDE.md), « Développement itératif »). Toute décision importante prise en cours de route est consignée dans un ADR (`docs/adr/`), et ce document est mis à jour en conséquence.

---

## 1. Principes d'architecture

| # | Principe | Conséquence |
|---|---|---|
| **AP-1** | **Tout sur site** (BR-14) | Aucun appel sortant ; NetworkPolicy qui refuse toute sortie ; aucun CDN ; registre et miroirs internes ; IA locale |
| **AP-2** | **Configuration plutôt que code** (BR-03) | Les champs métier sont des données (`field_definition`), jamais du code ; UI, recherche et IA sont pilotées par le schéma |
| **AP-3** | **API d'abord** (BR-15) | Contrat OpenAPI source de vérité ; le frontend n'a aucun accès privilégié |
| **AP-4** | **Asynchrone pour tout ce qui est lent** (BR-17) | Le dépôt est synchrone ; antivirus, OCR, rendition, IA et indexation passent par Kafka |
| **AP-5** | **Aucun binaire dans l'API ni en base** (BR-17) | Upload présigné ou en flux ; stockage objet adressé par SHA-256 |
| **AP-6** | **Sécurité par défaut** (BR-09) | Refus par défaut ; ACL appliquées dans les requêtes (SQL et OpenSearch) ; audit de toute action |
| **AP-7** | **Monolithe modulaire** | Un seul code, plusieurs déploiements (API, worker, importer) scalés indépendamment |
| **AP-8** | **Traçabilité et intégrité** (BR-12) | Audit append-only à chaîne de hash ; outbox transactionnelle |

## 2. Architecture

### 2.1 Vue d'ensemble

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

### 2.2 Composants

| Composant | Rôle | Technologie | Déploiement |
|---|---|---|---|
| `ged-web` | Interface utilisateur | React 18+, TypeScript, Vite, TanStack Query, PDF.js, bpmn-js | Nginx (UBI), Deployment |
| `ged-api` | API REST, sécurité, schéma, workflows | Java 21, Spring Boot 3.x, Spring Security, Flowable | Deployment + HPA (CPU) |
| `ged-worker` | Pipeline asynchrone | Spring Boot, Spring Kafka, Tika, Tesseract, client clamd, client Gotenberg, Spring AI | Deployment + HPA (lag Kafka) |
| `ged-importer` | Import massif | Spring Batch | Job OpenShift (à la demande) |
| `ged-core` | Domaine partagé (bibliothèque) | JPA, services, ports | — |
| PostgreSQL 16 | Source de vérité | `JSONB`, `ltree`, partitionnement | Opérateur interne / service DBA |
| Stockage objet | Binaires, renditions, texte, archives | S3 : ODF/NooBaa (prod), MinIO (dev) | Plateforme |
| OpenSearch | Recherche, facettes | OpenSearch 2.x | StatefulSet / opérateur |
| Kafka | Bus d'événements | AMQ Streams (Strimzi) | Opérateur |
| Keycloak | Identité | Red Hat build of Keycloak | Existant ou dédié |
| ClamAV | Antivirus | clamd + freshclam (miroir interne) | Deployment |
| Gotenberg | Conversion en PDF | LibreOffice headless | Deployment + HPA |
| Ollama | LLM | Gemma 4 | Instance interne existante |
| SMTP | Notifications | Relais interne | Existant |

### 2.3 Structure du code

```
ged/
├─ pom.xml              # parent : Java 21, BOM Spring Boot, versions
├─ ged-core/            # domaine, JPA, services, ports (ContentStorage, SearchIndex, DocumentClassifier…)
├─ ged-api/             # openapi/ged-v1.yaml, controllers (interfaces générées), sécurité, Flowable
├─ ged-worker/          # étapes du pipeline (consommateurs Kafka)
├─ ged-importer/        # jobs Spring Batch
├─ ged-web/             # SPA
├─ ged-perf/            # scénarios Gatling
├─ deploy/              # docker-compose, realm Keycloak, chart Helm
└─ docs/                # exigences, spécifications, backlog, ADR, exploitation
```

Les modules sont créés **au fil des itérations**. Les dépendances vont toujours de `ged-api`, `ged-worker` et `ged-importer` vers `ged-core`, jamais l'inverse.

Le domaine expose des **ports** (interfaces), implémentés par des adaptateurs. Exemples : `ContentStorage` → `S3ContentStorage`, `DocumentClassifier` → `OllamaClassifier`.

## 3. Modèle de données

### 3.1 Tables principales (PostgreSQL)

| Table | Colonnes clés | Notes |
|---|---|---|
| `folder` | `id uuid`, `parent_id`, `name`, `path ltree`, `created_at/by` | unique (`parent_id`, `name`) ; index GiST sur `path` |
| `document` | `id`, `folder_id`, `class_id`, `title`, `status`, `metadata jsonb`, `current_version_id`, `legacy_id`, `deleted_at`, `version` (optimiste) | GIN `jsonb_path_ops` sur `metadata` |
| `document_version` | `id`, `document_id`, `number`, `storage_key`, `sha256`, `size`, `mime`, `av_status`, `text_status`, `rendition_status`, `ai_status`, `created_at/by` | unique (`document_id`, `number`) |
| `document_class` | `id`, `code`, `labels jsonb`, `parent_id`, `retention_policy_id`, `watermark jsonb` | héritage sans cycle |
| `field_definition` | `id`, `code` (immuable), `type`, `labels jsonb`, `constraints jsonb`, `search jsonb`, `ai_hint` | `search` = recherchable, filtrable, facette, triable, colonne |
| `class_field` | `class_id`, `field_id`, `required_override`, `position` | |
| `value_list` / `value_list_item` | `code`, `labels`, `parent_id`, `active` | |
| `schema_revision` | `number`, `author`, `created_at`, `diff jsonb` | |
| `acl_entry` | `resource_type`, `resource_id`, `principal_type` (USER/GROUP), `principal_id`, `permission` (READ/WRITE/DELETE/ADMIN) | index (`resource_type`, `resource_id`) |
| `tag` / `document_tag` | | |
| `audit_event` | `id`, `ts`, `actor`, `client_id`, `ip`, `action`, `resource_type`, `resource_id`, `details jsonb`, `source`, `prev_hash`, `hash` | partitionnée par mois ; append-only |
| `outbox_event` | `id`, `aggregate_type`, `aggregate_id`, `type`, `payload jsonb`, `priority`, `created_at`, `published_at` | |
| `upload_session` | `id`, `s3_upload_id`, `document_id`, `state`, `expires_at` | |
| `retention_policy` | `id`, `duration`, `start_from` (dépôt ou champ date), `action` (DELETE/ARCHIVE) | |
| `legal_hold` / `legal_hold_document` | `id`, `name`, `reason`, `created_by`, `released_at` | |
| `classification_review` | `document_id`, `proposal jsonb`, `confidence`, `status` | |
| `workflow_assignment` | `class_id`, `process_definition_key`, `parameters jsonb`, `trigger` | |
| `idempotency_key` | `key`, `client_id`, `request_hash`, `response`, `expires_at` | |
| `webhook` / `webhook_delivery` | URL, secret, événements, statut, tentatives | |
| `import_job` | `id`, `manifest`, `dry_run`, `status`, `stats jsonb`, `report_key` | + tables `BATCH_*` de Spring Batch |
| Tables `ACT_*` / `FLW_*` | | gérées par Flowable |

### 3.2 Règles
- **Migrations** : uniquement par Flyway, en ajout uniquement ; une migration appliquée n'est jamais modifiée.
- **Identifiants** : UUID (v7 de préférence, pour la localité d'index).
- **Pagination** : keyset ou curseur exclusivement (jamais `OFFSET`).
- **Suppression** : logique (`deleted_at`), puis purge physique par la corbeille ou la rétention, sauf legal hold.
- **Audit** :
  - l'utilisateur applicatif n'a pas les droits `UPDATE`/`DELETE` sur `audit_event` ;
  - un trigger refuse ces opérations ;
  - une nouvelle partition est créée chaque mois par un job.

## 4. Stockage des binaires

| Préfixe S3 | Contenu | Clé |
|---|---|---|
| `content/` | originaux | `content/<sha256[0:2]>/<sha256>` |
| `renditions/` | PDF de prévisualisation (linéarisés) | `renditions/<sha256>.pdf` |
| `thumbnails/` | miniatures PNG | `thumbnails/<sha256>.png` |
| `text/` | texte extrait/OCR | `text/<sha256>.txt` |
| `tmp/` | dépôts en cours | expiration automatique |
| bucket `ged-archive` | archives (Object Lock) | idem `content/` |
| bucket `ged-import` | fichiers à importer | libre |

- **Téléchargement** : redirection 302 vers une URL présignée (durée de validité `ged.storage.presign-ttl`, 5 min par défaut), délivrée après contrôle de l'ACL.
- **Upload** :
  - fichiers jusqu'à 100 Mo : transmis en flux par l'API ;
  - au-delà : multipart présigné, directement du client vers S3 ;
  - le SHA-256 est calculé en flux, avec déduplication par la clé.
- **Bucket** : CORS limité à l'origine de `ged-web` ; en-têtes `Range` autorisés pour PDF.js.

## 5. Schéma configurable

- **Types de champ** :
  - `TEXT`, `LONG_TEXT`
  - `INTEGER`, `DECIMAL`, `AMOUNT` (valeur + devise)
  - `DATE`, `DATETIME`
  - `BOOLEAN`
  - `LIST` (référence à une `value_list`)
  - `USER_GROUP`
  - `DOCUMENT_LINK`
- **Contraintes** (`constraints`) : `required`, `multiValued`, `unique`, `default`, `regex`, `min`, `max`, `minLength`, `maxLength`.
- **Validation** : `MetadataValidator` (dans `ged-core`) est **le seul point de validation**. Il est utilisé par l'API, l'import et l'application des propositions de l'IA. Une erreur renvoie `422` avec `errors[]` par champ.
- **Cache** : `SchemaCache` (Caffeine), invalidé par l'événement Kafka `schema.changed`, pour que tous les pods restent cohérents.
- **JSON Schema** : `JsonSchemaGenerator` produit le schéma effectif d'une classe, héritage compris. Il sert à `GET /classes/{id}/json-schema`, à la validation côté client et au `format` de sortie de l'IA.
- **Changements cassants** (type modifié, champ supprimé, passage en obligatoire) : ils déclenchent `ReindexJob` (§ 6.3) et produisent un rapport des documents non conformes.
- **Export/import** : YAML (classes, champs, listes, BPMN), au format documenté dans `docs/schema-format.md`. L'import propose un mode simulation (diff).

## 6. Recherche (OpenSearch)

### 6.1 Index
- Index physique `ged-documents-vN`, toujours accédé via l'alias `ged-documents`.
- **Dimensionnement pour 50 M de documents** :
  - hypothèse de ~5 Ko de texte indexé par document, soit **~250 à 400 Go** d'index primaire ;
  - viser des shards de 20 à 40 Go, soit **~10 à 16 shards primaires** et 1 réplica, sur au moins 3 nœuds de données ;
  - valeurs à confirmer par les tests de charge (itération 33) ;
  - l'alias permet de passer plus tard à plusieurs index (par exemple par période) sans changer l'application.

### 6.2 Mapping

```json
{
  "dynamic": "strict",
  "properties": {
    "id":         { "type": "keyword" },
    "language":   { "type": "keyword" },
    "title":      { "type": "text", "analyzer": "standard", "fields": {
                      "fr": { "type": "text", "analyzer": "french" },
                      "de": { "type": "text", "analyzer": "german" },
                      "en": { "type": "text", "analyzer": "english" },
                      "raw": { "type": "keyword" } } },
    "content":    { "type": "text", "analyzer": "standard", "fields": {
                      "fr": { "type": "text", "analyzer": "french" },
                      "de": { "type": "text", "analyzer": "german" },
                      "en": { "type": "text", "analyzer": "english" } } },
    "class":      { "type": "keyword" },
    "folder_path":{ "type": "keyword" },
    "status":     { "type": "keyword" },
    "tags":       { "type": "keyword" },
    "created_at": { "type": "date" },
    "allowed_principals": { "type": "keyword" },
    "meta": { "type": "object", "dynamic": "strict", "properties": {
      "numero_facture": { "type": "keyword" },
      "montant":        { "type": "scaled_float", "scaling_factor": 100 },
      "echeance":       { "type": "date" }
    } }
  }
}
```

**Recherche multilingue (fr, de, en)** :
- `title` et `content` sont indexés une fois par langue, avec les analyseurs `french`, `german` et `english` (racinisation, mots vides), plus un champ `standard` de secours.
- La langue de chaque document est détectée par Tika à l'extraction et stockée dans `language`.
- Une recherche interroge les trois sous-champs (`multi_match` sur `content.fr`, `content.de`, `content.en` et `content`). Un utilisateur trouve donc un document allemand en tapant un mot allemand, depuis une interface en français.
- Le filtre « langue du document » est disponible en facette.

Les propriétés sous `meta` sont **ajoutées par `MappingSynchronizer`** (`PUT _mapping` additif) lors de la création d'un champ :

| `FieldType` | Mapping |
|---|---|
| `TEXT` | `text` + sous-champ `keyword` |
| `LONG_TEXT` | `text` |
| `INTEGER` | `long` |
| `DECIMAL` | `double` |
| `AMOUNT` | `scaled_float` + `keyword` pour la devise |
| `DATE`, `DATETIME` | `date` |
| `BOOLEAN` | `boolean` |
| `LIST`, `USER_GROUP`, `DOCUMENT_LINK` | `keyword` |

### 6.3 Règles
- **Droits** :
  - toute requête contient un filtre `terms` sur `allowed_principals`, alimenté par l'utilisateur et ses groupes ;
  - le filtre s'applique **avant** les agrégations, ce qui protège aussi les compteurs ;
  - un changement d'ACL émet `acl.changed`, qui déclenche la réindexation du sous-arbre (par lots).
- **Requête** : `POST /search` (`multi_match` + filtres + agrégations + `highlight`), paginée par `search_after`.
- **Réindexation** : création de `ged-documents-vN+1`, alimentation depuis PostgreSQL et S3 (`text/`), bascule atomique de l'alias, puis suppression de l'ancien index après vérification.
- **Chargement initial** : `_bulk`, avec `refresh_interval: -1` et `number_of_replicas: 0` pendant l'import, rétablis à la fin.

## 7. Pipeline asynchrone

### 7.1 Événements et topics Kafka

| Topic | Producteur | Consommateur | Contenu |
|---|---|---|---|
| `ged.version.created` | outbox (api, importer) | antivirus | nouvelle version |
| `ged.version.scanned` | antivirus | extraction, rendition | statut AV `CLEAN` |
| `ged.text.extracted` | extraction | indexation, IA | texte disponible |
| `ged.rendition.ready` | rendition | — (notification UI) | aperçu disponible |
| `ged.document.changed` | outbox | indexation, webhooks | métadonnées, statut |
| `ged.acl.changed` | outbox | indexation | droits modifiés |
| `ged.schema.changed` | outbox | tous (cache) | révision de schéma |
| `ged.workflow.event` | outbox | webhooks | étapes de workflow |
| `*.bulk` | importer | consommateurs dédiés | variantes basse priorité |
| `*.dlq` | gestionnaire d'erreurs | administration (rejeu) | messages en échec |

### 7.2 Règles
- **Outbox** :
  - les événements sont écrits dans `outbox_event`, **dans la transaction métier** ;
  - le relais publie par lots (`FOR UPDATE SKIP LOCKED`), puis marque et purge.
- **Consommateurs** : idempotents (clé = id de version + étape) ; livraison *at-least-once*.
- **Ordre des étapes** : antivirus → (extraction + OCR) ∥ rendition → indexation → IA. Aucune étape ne s'exécute avant un statut antivirus `CLEAN`.
- **Erreurs** : nouvelles tentatives à délai croissant, puis DLQ ; rejeu par l'administration.
- **Mise à l'échelle** : HPA sur le lag Kafka (KEDA ou métriques externes). Les consommateurs `*.bulk` sont déployés séparément.

### 7.3 Étapes

| Étape | Outil | Détails |
|---|---|---|
| Antivirus | clamd `INSTREAM` | `INFECTED` → quarantaine, téléchargement bloqué, audit, notification |
| Extraction | Apache Tika | texte natif (PDF, Office, email…) |
| OCR | Tesseract (`fra+deu+eng`, configurable) | si texte natif insuffisant ; 300 DPI ; délai max par page |
| Rendition | Gotenberg (LibreOffice) → `qpdf --linearize` | PDF → copie linéarisée ; Office/ODF/RTF → PDF ; TIFF/images → PDF ; email → HTML → PDF ; échec → `UNSUPPORTED` + DLQ |
| Miniature | PDFBox | première page en PNG |
| Indexation | client Java OpenSearch | document complet, avec `allowed_principals` |
| IA | Spring AI + Ollama | voir § 9 |

## 8. Sécurité

### 8.1 Authentification
- **Utilisateurs** : OIDC Authorization Code + PKCE (client public `ged-web`) ; `ged-api` est un resource server JWT.
- **Applications** : *client credentials* ; scopes `documents:read`, `documents:write`, `schema:admin`, `workflow:act`, `compliance:admin`, `import:run`.
- **Rôles** : `ged-admin` (administration fonctionnelle), `ged-compliance` (conformité). Les groupes viennent du claim `groups`.

### 8.2 Autorisation
- **Modèle** : ACL (READ < WRITE < DELETE < ADMIN) sur les dossiers et documents, **héritées** par l'arborescence.
- **Vérification** : une seule requête SQL sur les ancêtres (`folder.path @> …`), qui combine utilisateur et groupes. Jamais de parcours un par un, jamais de filtrage en mémoire.
- **Sans droit READ** : `404`, ce qui ne révèle pas l'existence du document. **Sans droit WRITE ou DELETE** : `403`.
- **Recherche** : filtre `allowed_principals` (§ 6.3).
- **Créateur** : il reçoit le droit ADMIN sur ce qu'il crée, sauf politique contraire définie sur le dossier parent.

### 8.3 Réseau et plateforme
- NetworkPolicy *egress* qui refuse tout par défaut, avec des autorisations explicites par service interne.
- Routes en TLS ; communication interne en TLS si la plateforme le permet (service mesh **[à confirmer]**).
- Images UBI depuis le registre interne, exécutées en utilisateur non-root, avec scan de vulnérabilités en CI.
- Secrets dans des Secrets OpenShift (ou un coffre interne **[à confirmer]**), jamais dans le dépôt.
- Content Security Policy stricte sur `ged-web` (`default-src 'self'`).

### 8.4 Workflows
- La validation BPMN se fait côté serveur : liste blanche des éléments et des delegates GED ; `scriptTask`, `class` et expressions arbitraires refusés.
- Le contexte d'expressions Flowable est restreint aux beans de la liste blanche.

### 8.5 Webhooks
- Destinations limitées par une liste blanche de domaines et réseaux internes.
- Signature HMAC-SHA256 (en-tête `X-GED-Signature`), horodatage anti-rejeu.

## 9. Classification IA

- **Interface** : `DocumentClassifier.classify(DocumentText, List<ClassDefinition>) → Classification` (classe, valeurs, confiance globale et par champ).
- **Implémentation** : `OllamaClassifier`, via Spring AI (module Ollama), avec le modèle `gemma4`.
- **Prompt** : il contient les classes candidates (libellés, descriptions), les consignes d'extraction (`ai_hint`) et le texte tronqué à `ged.ai.max-pages` pages.
- **Sortie contrainte** : `format` = JSON Schema généré (§ 5).
- **Seuils** :
  - au-dessus de `ged.ai.auto-apply-threshold` : application automatique, auditée comme « proposé par IA » ;
  - en dessous : file `classification_review`.
- **Débit** : consommateur dédié, avec une concurrence `ged.ai.concurrency` alignée sur la capacité d'Ollama. Les nouveaux dépôts sont prioritaires sur l'import.
- **Désactivation** : `ged.ai.enabled=false`.
- **Évolution** : remplacement d'Ollama par vLLM (API compatible OpenAI) sans modifier l'interface.
- **Évaluation** : jeu de référence annoté (≥ 20 documents), avec un rapport de précision par champ.

| Propriété | Défaut |
|---|---|
| `ged.ai.enabled` | `true` |
| `ged.ai.base-url` | URL de l'Ollama interne |
| `ged.ai.model` | `gemma4` |
| `ged.ai.max-pages` | `3` |
| `ged.ai.concurrency` | `2` |
| `ged.ai.auto-apply-threshold` | `0.85` |
| `ged.ai.timeout` | `120s` |

## 10. Workflows (Flowable)

- **Moteur** : Flowable embarqué dans `ged-api`, avec ses tables dans PostgreSQL.
- **Delegates GED** : `SetStatus`, `Lock`/`Unlock`, `UpdateField`, `Notify` (SMTP interne, modèles Thymeleaf), `Archive`, `CallSubprocess`.
- **Modèles par défaut** (`ged-core/src/main/resources/workflows/`), déployés au premier démarrage, protégés et versionnés :

| Clé | Circuit | Paramètres |
|---|---|---|
| `ged-validation-simple` | 1 valideur | `group`, `dueIn` |
| `ged-double-validation` | 2 valideurs successifs | `group1`, `group2`, `dueIn` |
| `ged-validation-seuil` | 2e valideur si `field > threshold` | `field`, `threshold`, `group1`, `group2` |
| `ged-validation-collegiale` | multi-instance parallèle | `group`, `quorum` (ALL / ANY / n) |
| `ged-relecture-publication` | rédaction → relecture → approbation → publication | `reviewers`, `approvers` |
| `ged-prise-connaissance` | accusé de lecture par membre, avec relance | `group`, `remindEvery` |

- **Cycle de vie** : brouillon → validation serveur → publication (nouvelle version de la définition). Les instances en cours restent sur leur version.
- **Déclenchement** : sur `document.created` si un `workflow_assignment` existe pour la classe, ou manuellement.
- **Escalades** : *boundary timer events*.
- **Audit** : un `ExecutionListener` global produit un `audit_event` par transition.
- **Éditeur** : bpmn-js + properties panel, avec un *provider* GED (groupes, délais, conditions sur les champs `metadata.<code>`).

## 11. Prévisualisation

- **Statuts** : `GET /documents/{id}/preview` → `{ status: READY | PENDING | UNSUPPORTED, url, pages }`.
- **Génération** :
  - à l'ingestion pour les dépôts unitaires ;
  - **à la demande puis mise en cache** pour les documents importés en masse ;
  - un job peut pré-générer les classes les plus consultées.
- **Visionneuse** : PDF.js (worker embarqué), avec chargement progressif par requêtes `Range` sur l'URL présignée.
- **Filigrane** : calque dessiné par la visionneuse, selon `document_class.watermark`.
- **Original intact** : l'original n'est jamais modifié ; le téléchargement renvoie toujours `content/`.

## 12. API REST

### 12.1 Conventions

| Sujet | Règle |
|---|---|
| Contrat | `ged-api/src/main/resources/openapi/ged-v1.yaml` (OpenAPI 3.1), lint Spectral, code serveur et client TS générés |
| Version | préfixe `/api/v1` ; aucun changement cassant dans une version publiée |
| Format | JSON (UTF-8) ; dates ISO 8601 UTC |
| Erreurs | RFC 9457 `application/problem+json`, avec `type` stable et `errors[]` pour la validation |
| Pagination | `?cursor=&limit=` (max 200) ; réponse `{ items, nextCursor }` |
| Concurrence | `ETag` (version d'entité) + `If-Match` → `412` |
| Idempotence | `Idempotency-Key` sur les POST de création (conservée 24 h) |
| Quotas | Bucket4j par `client_id` → `429` + `Retry-After` |
| Documentation | Swagger UI + Redoc servis sur `/api/docs` (sans CDN) |

### 12.2 Ressources
Voir la section « API REST publique » de [CLAUDE.md](../CLAUDE.md) pour la liste complète des endpoints par domaine : schéma, dossiers, documents, fichiers, prévisualisation, recherche, workflows, tâches, IA, conformité, imports, webhooks.

### 12.3 Codes de retour usuels

| Code | Cas |
|---|---|
| `200`/`201`/`204` | succès |
| `302` | téléchargement (URL présignée) |
| `400` | requête mal formée |
| `401` | non authentifié |
| `403` | droit insuffisant (WRITE, DELETE, ADMIN) |
| `404` | inexistant **ou** non lisible |
| `409` | conflit métier (doublon de nom, dossier non vide, legal hold) |
| `412` | `If-Match` périmé |
| `413` | fichier trop gros pour l'envoi direct |
| `422` | validation des métadonnées ou du BPMN |
| `429` | quota dépassé |

## 13. Import massif (Spring Batch)

- **Exécution** : module `ged-importer`, en Job OpenShift ; `JobRepository` dans PostgreSQL (tables `BATCH_*`).
- **Entrée** : manifeste CSV ou JSONL (`docs/import-format.md`), avec pour chaque ligne le fichier, la classe, les métadonnées, le dossier et les ACL. Fichiers dans le bucket `ged-import` ou sur un partage NFS/SMB monté en lecture seule.
- **Step** : `reader` (`FlatFileItemReader` / `JsonItemReader`) → `processor` → `writer`, en chunks de 500 (configurable).
  - Le **processor** vérifie le fichier, calcule le SHA-256 (copie serveur à serveur `CopyObject` + déduplication), valide via `MetadataValidator` et résout le dossier.
  - Le **writer** fait des inserts JDBC batch (documents, versions), écrit l'audit par `COPY` et publie l'outbox sur les topics `*.bulk`.
- **Robustesse** :
  - **idempotence par ligne** (clé `import_id` + n° de ligne) : une reprise ne crée pas de doublon ;
  - `skipLimit` configurable ; `SkipListener` vers le rapport.
- **Parallélisme** : `Partitioner` par plages de lignes, partitions réparties sur plusieurs pods.
- **Modes** :
  - `dryRun` : validation complète sans écriture ;
  - relance limitée aux lignes en erreur.
- **Optimisations** : OCR évité si le texte est fourni ou natif ; IA évitée si les métadonnées sont fournies ; rendition à la demande ; réglages OpenSearch de chargement initial.

## 14. Conformité et archivage

- **Durée** : **20 ans pour tous les documents** (`ged.retention.default=P20Y`). Les politiques par classe restent possibles techniquement, mais aucune n'est prévue.
- **Rétention** : `RetentionJob` (`@Scheduled` + ShedLock), traité par lots. Point de départ : date de dépôt ou valeur d'un champ date. Action : suppression ou archivage. Les documents sous legal hold sont exclus.
- **Legal hold** : vérifié dans **toutes** les voies de suppression (API, corbeille, rétention, import).
- **Corbeille** : suppression logique, puis purge après `ged.trash.retention` (30 jours par défaut), sauf legal hold.
- **Archivage** : copie vers `ged-archive` avec Object Lock (`RetainUntilDate` = fin de rétention). Mode COMPLIANCE ou GOVERNANCE : **[décision T44.1]**.
- **Audit** :
  - chaîne de hash `hash = SHA-256(prev_hash || contenu canonique)`, avec une insertion sérialisée ;
  - `AuditVerifier` vérifie la chaîne sur une période ;
  - export CSV/JSON en flux ;
  - partitions froides archivées vers S3 (format **[décision T45.3]**), avec leur empreinte.

## 15. Exigences non fonctionnelles

| # | Exigence | Cible |
|---|---|---|
| **NFR-01** | Volumétrie | **500 utilisateurs, 50 millions de documents.** Hypothèses de dimensionnement, à valider : **100 utilisateurs simultanés** en pointe (20 %) ; taille moyenne de 300 Ko par original, soit **~15 To d'originaux**, plus les renditions et le texte (~+30 %) ; 2 réplicas S3 → **~40 To bruts** à prévoir. |
| **NFR-02** | Latence de recherche | p95 < 500 ms (100 000 documents en test, puis extrapolation) |
| **NFR-03** | Latence API (hors transfert) | p95 < 300 ms sur les lectures unitaires |
| **NFR-04** | Délai d'indexation | document cherchable par ses métadonnées en moins de 10 s, par son contenu dès la fin de l'OCR |
| **NFR-05** | Taille de fichier | jusqu'à 5 Go en multipart (configurable) |
| **NFR-06** | Disponibilité et reprise | **RTO = 6 h** (remise en service après sinistre), **RPO = 6 h** (données perdues au maximum). API, worker et web sans état et redondés (≥ 2 réplicas). Taux de disponibilité et heures de service **[À définir : Q5]** |
| **NFR-07** | Sauvegarde | PostgreSQL (PITR) et S3 répliqués **en continu** vers un second site interne **en Suisse**. Le RPO de 6 h autorise une réplication **asynchrone**, mais c'est le **RTO** qui impose la solution. Avec un RTO de 6 h, la reprise ne peut pas reposer sur une restauration complète des sauvegardes de 15 To : il faut une **réplication vers le second site** (S3 multi-site, réplica PostgreSQL) et une **procédure de bascule testée**. L'index OpenSearch se reconstruit depuis PostgreSQL et S3 ; pour tenir les 6 h, il faut aussi le répliquer ou le sauvegarder par *snapshots* sur le second site. |
| **NFR-13** | Données sensibles (nLPD, LPGA) | Chiffrement **au repos** (S3 SSE avec clés gérées en interne, chiffrement des volumes PostgreSQL et OpenSearch) et **en transit** (TLS) ; journalisation de **toutes les consultations** ; droits au besoin d'en connaître ; aucune donnée personnelle dans les logs techniques |
| **NFR-14** | Durée de conservation | **20 ans par défaut** ; formats lisibles sur toute la durée (originaux conservés tels quels, **pas de conversion PDF/A**) ; vérification périodique de l'intégrité des fichiers (SHA-256) |
| **NFR-15** | Navigateurs | Chrome, Edge et Firefox (dernières versions) |
| **NFR-16** | Langues | **fr, de, en** : interface (i18n `ged-web`), libellés (`labels` = `{fr, de, en}`, au moins une langue obligatoire, avec repli), emails, OCR (`fra+deu+eng`), recherche multilingue (§ 6.2), prompt IA adapté à la langue détectée |
| **NFR-08** | Scalabilité | horizontale pour l'API, le worker, Gotenberg et l'importer ; OpenSearch par shards |
| **NFR-09** | Souveraineté | zéro flux sortant, vérifié par un test de recette (NetworkPolicy) |
| **NFR-10** | Sécurité | OWASP ASVS niveau 2 **[à confirmer]** ; aucune vulnérabilité critique en CI |
| **NFR-11** | Observabilité | métriques Micrometer/Prometheus, logs JSON (Loki/EFK interne), traces OpenTelemetry vers un collecteur interne |
| **NFR-12** | Maintenabilité | couverture de tests ≥ 80 % sur `ged-core` ; ADR pour chaque décision structurante |

## 16. Déploiement

- **Chart Helm** `deploy/helm/ged` :
  - Deployments `ged-api`, `ged-worker`, `ged-web`, `gotenberg`, `clamav` ;
  - Job `ged-importer` ;
  - Routes, HPA, NetworkPolicies, ConfigMaps, références de Secrets.
- **Environnements** : dev (docker-compose), recette (namespace dédié), production. Valeurs par environnement : `values-<env>.yaml`.
- **Base de données** : Flyway à chaque démarrage de `ged-api`, avec verrou pour un seul pod à la fois.
- **CI** :
  - build Maven et npm via les miroirs internes ;
  - tests ;
  - lint du contrat ;
  - scan des images ;
  - push vers le registre interne.
- **Configuration** : propriétés `ged.*` (stockage, IA, quotas, rétention), surchargées par des variables d'environnement.

## 17. Stratégie de tests

| Niveau | Outil | Portée |
|---|---|---|
| Unitaires | JUnit 5, AssertJ, Mockito | services, `MetadataValidator`, `BpmnValidator`, générateurs |
| Intégration | Testcontainers (PostgreSQL, MinIO, Kafka, OpenSearch, Keycloak) | repositories, pipeline, sécurité, ACL |
| Contrat | validation OpenAPI des réponses | conformité au contrat `ged-v1.yaml` |
| Frontend | Vitest + Testing Library | composants, formulaires dynamiques |
| Bout en bout | scénarios de la section « Vérification » de CLAUDE.md | parcours utilisateurs (UR, § 5) |
| Performance | Gatling (`ged-perf`) | NFR-02, NFR-03, débits OCR/IA |
| Sécurité | tests ACL dédiés, scan d'images, test de sortie réseau | NFR-09, NFR-10 |
| IA | jeu de référence annoté | précision par champ |

## 18. Décisions d'architecture (ADR)

| ADR | Décision | Statut |
|---|---|---|
| 0001 | Contrat d'abord (OpenAPI, code généré) | à rédiger (itération 3) |
| 0002 | Aucune sortie réseau (NetworkPolicy, pas de CDN) | à rédiger (itération 5) |
| 0003 | Stockage adressé par contenu (SHA-256) | à rédiger (itération 7) |
| 0004 | Métadonnées en JSONB pilotées par le schéma | à rédiger (itération 11) |
| 0005 | OpenSearch avec mapping strict et filtrage des ACL dans la requête | à rédiger (itération 18) |
| 0006 | Outbox transactionnelle + Kafka | à rédiger (itération 16) |
| 0007 | Gotenberg pour les renditions | à rédiger (itération 22) |
| 0008 | IA locale Ollama/Gemma 4 derrière `DocumentClassifier` | à rédiger (itération 24) |
| 0009 | Flowable embarqué et BPMN restreint | à rédiger (itération 25) |
| 0010 | Mode Object Lock | **décision attendue (T44.1)** |
| 0011 | Spring Batch pour l'import massif | à rédiger (itération 30) |

## 19. Points ouverts techniques

| # | Sujet | Impact |
|---|---|---|
| TQ-1 | Mode Object Lock : COMPLIANCE ou GOVERNANCE (T44.1) | archivage |
| TQ-2 | Format d'archivage des partitions d'audit (T45.3) | conformité |
| TQ-3 | Stockage des quotas Bucket4j (T50.1) | API |
| TQ-4 | Capacité et concurrence de l'instance Ollama existante | débit IA |
| TQ-5 | Service mesh / mTLS interne disponible ? | sécurité |
| TQ-6 | Coffre de secrets interne (Vault…) disponible ? | déploiement |
| TQ-7 | Opérateurs disponibles sur la plateforme (PostgreSQL, Kafka, OpenSearch) | déploiement |
| TQ-8 | RTO 6 h et RPO 6 h ✅ ; caractéristiques du second site (en Suisse, réplication S3 et PostgreSQL disponibles ?) | NFR-06, NFR-07 |
| TQ-9 | Taille moyenne réelle des documents et croissance annuelle, pour confirmer les ~40 To bruts | NFR-01 |
| TQ-10 | Gestion des clés de chiffrement (KMS ou HSM interne ?) | NFR-13 |
| TQ-11 | ✅ fr, de, en (NFR-16). Reste à vérifier : qualité de la détection de langue sur les documents courts | UQ-05 |
