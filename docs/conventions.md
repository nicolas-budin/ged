# Conventions de code

Ce document rassemble les règles de code du projet. Il grandit au fil des itérations : chaque fois qu'un choix de style ou de structure est fait et validé, il est ajouté ici.

Les règles d'architecture (souveraineté, sécurité, volumétrie) sont dans [CLAUDE.md](../CLAUDE.md), section « Règles à respecter ».

---

## Java

### Packages
- Racine : `ch.louhan.ged`.
- Un sous-package par module : `ch.louhan.ged.api`, et plus tard `ch.louhan.ged.core`, `ch.louhan.ged.worker`, `ch.louhan.ged.importer`.
- Dans un module, les classes sont regroupées **par fonctionnalité**, et non par couche technique. Par exemple, `api.ping`, puis `api.folder` et `api.document`, plutôt que `api.controller` et `api.service`.

### Injection de dépendances
- **Par constructeur**, avec des champs **`final`**, **sans `@Autowired`**. Quand une classe a un seul constructeur, Spring l'utilise automatiquement.

  ```java
  private final BuildProperties buildProperties;

  public PingController(BuildProperties buildProperties) {
      this.buildProperties = buildProperties;
  }
  ```
- **Interdit dans le code de production** : `@Autowired` sur un champ.
- **Toléré dans les tests** : `@Autowired` sur un champ. Les classes de test sont créées par JUnit, pas par Spring.
- **Pourquoi** :
  - les dépendances sont immuables et visibles ;
  - la classe se teste sans Spring ;
  - un objet ne peut jamais exister à moitié construit.
- **Signal d'alerte** : un constructeur avec beaucoup de paramètres indique une classe qui en fait trop.

### Objets de données
- Les objets échangés par l'API (JSON) sont **générés depuis le contrat OpenAPI**, avec le suffixe **`Dto`** (`FolderDto`, `CreateFolderRequestDto`), pour les distinguer des entités JPA (`Folder`). On ne les écrit pas à la main, et on ne modifie jamais le code généré (`target/generated-sources`, non committé).
- Les objets internes simples (résultat d'un service, par exemple `FolderService.Page`) sont des **records Java**, immuables.
- La conversion entité → DTO se fait dans le controller, avec des méthodes `toDto(...)` privées.

### Organisation d'une fonctionnalité
Pour chaque fonctionnalité (par exemple `api.folder`) :

| Classe | Rôle | Règle |
|---|---|---|
| `XxxController` | **implémente l'interface générée** `XxxApi` : appelle le service, convertit entité → DTO | **aucune règle métier, aucune annotation de route** (elles sont dans l'interface générée) |
| `XxxService` | règles métier, transactions (`@Transactional`) | seul point d'entrée vers le repository |
| `XxxRepository` | accès à la base (Spring Data) | requêtes de liste en `@Query` explicite |
| `Xxx` | entité JPA | constructeur `protected` vide pour JPA ; pas de setters publics, mais des méthodes métier (`rename(…)`) |

- **Une classe par fichier**, y compris les exceptions.
- Les exceptions métier **héritent de `GedException`** (package `api.error`), avec un statut HTTP et un **code stable** en kebab-case : `super(HttpStatus.CONFLICT, "folder-not-empty", "…")`. Le code est documenté dans le contrat et ne change jamais.

### Interfaces
- **Pas d'interface pour les services métier** (`FolderService` est une classe, sans `FolderServiceImpl`). Spring n'en a plus besoin pour `@Transactional`, et Mockito sait simuler une classe. Une interface avec une seule implémentation n'apporte que du code à maintenir (principe YAGNI).
- **Une interface uniquement** :
  - pour **isoler une technologie remplaçable** : `ContentStorage` (S3), `DocumentClassifier` (Ollama, puis vLLM), `SearchIndex` (OpenSearch) ;
  - ou quand il existe **réellement plusieurs implémentations**.
- Les interfaces des controllers (`XxxApi`) sont **générées** depuis le contrat OpenAPI : c'est un cas à part.

### Validation
- Les règles de validation de l'API (obligatoire, longueur, motif, bornes) sont écrites **dans le contrat** (`required`, `maxLength`, `pattern`, `minimum`…). Le générateur les traduit en annotations Jakarta Validation sur l'interface générée.
- Les interfaces générées portent `@Validated`. Une violation lève alors `ConstraintViolationException`, que `ApiExceptionHandler` convertit en **400** (sans lui, ce serait un 500).
- **Ne pas ajouter `@Validated` sur nos propres classes** : ce serait redondant.
- Les règles **métier** (nom déjà pris, dossier non vide…) restent dans les services.

### Commentaires
- En **français**.
- La Javadoc explique le **pourquoi** et le rôle de la classe, pas ce que le code dit déjà.

## Base de données
- Le schéma est géré **uniquement par Flyway** (`src/main/resources/db/migration/V<n>__<description>.sql`). Hibernate est en `ddl-auto: validate`.
- **Une migration appliquée n'est jamais modifiée** : chaque évolution passe par un nouveau fichier.
- Les contraintes d'intégrité sont **aussi en base** (unicité, clés étrangères), et pas seulement dans le code Java.
- Les identifiants sont des `UUID`.
- `open-in-view: false` : aucun accès à la base en dehors des services.
- **Listes : pagination par curseur (keyset), jamais `OFFSET`.** Le curseur est opaque pour le client (Base64), et on lit `limit + 1` lignes pour savoir s'il existe une page suivante.

## API REST
- Comprendre le fonctionnement : [guide OpenAPI et Swagger](guides/openapi-swagger.md).
- **Contrat d'abord** : toute évolution de l'API commence dans `ged-api/src/main/resources/openapi/ged-v1.yaml`, avant le code Java. Puis `./mvnw compile` régénère les interfaces, et on implémente.
- Lint du contrat : `npx @stoplight/spectral-cli lint ged-api/src/main/resources/openapi/ged-v1.yaml` (règles dans `.spectral.yaml`) doit renvoyer **0 erreur**.
- Dans le YAML, mettre entre guillemets toute valeur qui contient `: ` (deux-points + espace).
- Toutes les routes publiques sont préfixées par **`/api/v1`** : préfixe ajouté une seule fois par `WebConfig` (les chemins du contrat sont relatifs : `/folders`).
- **Erreurs : format RFC 9457** (`application/problem+json`), produit par `ApiExceptionHandler`. Toute erreur porte `type` (`urn:ged:problem:<code>`), `status`, `detail` et **`code`** ; les erreurs de validation ajoutent `errors: [{field, message}]`. Une erreur imprévue donne un 500 générique, sans détail technique pour le client.
- Création : `201 Created` + en-tête `Location`. Suppression : `204 No Content`.
- Listes : `{"items": [...], "nextCursor": "..." | null}`, avec les paramètres `limit` (1 à 200, 50 par défaut) et `cursor`.
- Les endpoints techniques (`/actuator/*`) ne font pas partie de l'API publique. Seuls `health` et `info` sont exposés.

## Tests

| Type | Suffixe | Dossier | Exécuté par | Usage |
|---|---|---|---|---|
| Unitaire | `*Test.java` | `src/test/java` | surefire (`./mvnw test`) | une classe seule, sans Spring, rapide |
| Intégration | `*IT.java` | `src/test/java` | failsafe (`./mvnw verify`) | application démarrée (`@SpringBootTest`), avec base et services via Testcontainers |

- Le test est placé **dans le même package** que la classe testée.
- Les tests d'intégration héritent de **`AbstractIntegrationTest`** : application complète + PostgreSQL dans Testcontainers, avec un conteneur partagé par toute la suite.
- **Tests de contrat** : sur toute requête valide, ajouter `.andExpect(RESPECTE_LE_CONTRAT)`, qui vérifie la requête **et** la réponse par rapport à `ged-v1.yaml` (code HTTP déclaré, JSON, format des erreurs). Ne pas l'utiliser sur une requête volontairement invalide : le validateur rejetterait la requête elle-même.
- Chaque test part d'un **état connu** (`TRUNCATE` dans `@BeforeEach`). Un test ne dépend jamais d'un autre.
- Les méthodes de test portent un **nom en français**, qui décrit le comportement attendu : `ping_renvoie_ok_et_la_version()`.
- `./mvnw verify` doit passer avant toute validation d'itération.

## Git
- **Un commit par itération**, fait après validation du product owner.
- Message : `Itération N : <titre> (US-xx)`. Exemple : `Itération 1 : Hello GED (US-01)`.
- Dans [docs/stories.md](stories.md), les tâches terminées sont cochées (`- [x]`) dans le même commit.

## Build
- Toujours utiliser le wrapper **`./mvnw`**, jamais `mvn` directement : il garantit la version de Maven, et le `mvn` de Homebrew peut utiliser une autre version de Java.
- Java **25**, Spring Boot **4.1**. Les versions sont fixées dans le [pom.xml](../pom.xml) parent.
