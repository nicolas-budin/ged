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
- Les réponses et requêtes simples sont des **records Java**, immuables. Par exemple, `PingResponse`.
- À partir de l'itération 3, les objets échangés par l'API sont **générés depuis le contrat OpenAPI**. On ne les écrit plus à la main, et on ne modifie jamais le code généré.

### Organisation d'une fonctionnalité
Pour chaque fonctionnalité (par exemple `api.folder`) :

| Classe | Rôle | Règle |
|---|---|---|
| `XxxController` | HTTP ⇄ Java : routes, validation de l'entrée, conversion en JSON | **aucune règle métier** |
| `XxxService` | règles métier, transactions (`@Transactional`) | seul point d'entrée vers le repository |
| `XxxRepository` | accès à la base (Spring Data) | requêtes de liste en `@Query` explicite |
| `Xxx` | entité JPA | constructeur `protected` vide pour JPA ; pas de setters publics, mais des méthodes métier (`rename(…)`) |

- **Une classe par fichier**, y compris les exceptions.
- Les exceptions métier portent `@ResponseStatus` (404, 409, 400…). Elles passeront au format RFC 9457 à l'itération 3.

### Validation
- On valide avec Jakarta Validation (`@NotBlank`, `@Size`, `@Min`…) sur les records de requête et les paramètres.
- **Ne pas mettre `@Validated` sur un controller** : Spring MVC valide déjà, et répond 400. `@Validated` activerait un autre mécanisme, qui répond 500.

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
- Toutes les routes publiques sont préfixées par **`/api/v1`**.
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
