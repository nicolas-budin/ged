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

### Commentaires
- En **français**.
- La Javadoc explique le **pourquoi** et le rôle de la classe, pas ce que le code dit déjà.

## API REST
- Toutes les routes publiques sont préfixées par **`/api/v1`**.
- Les endpoints techniques (`/actuator/*`) ne font pas partie de l'API publique. Seuls `health` et `info` sont exposés.

## Tests

| Type | Suffixe | Dossier | Exécuté par | Usage |
|---|---|---|---|---|
| Unitaire | `*Test.java` | `src/test/java` | surefire (`./mvnw test`) | une classe seule, sans Spring, rapide |
| Intégration | `*IT.java` | `src/test/java` | failsafe (`./mvnw verify`) | application démarrée (`@SpringBootTest`), avec base et services via Testcontainers |

- Le test est placé **dans le même package** que la classe testée.
- Les méthodes de test portent un **nom en français**, qui décrit le comportement attendu : `ping_renvoie_ok_et_la_version()`.
- `./mvnw verify` doit passer avant toute validation d'itération.

## Git
- **Un commit par itération**, fait après validation du product owner.
- Message : `Itération N : <titre> (US-xx)`. Exemple : `Itération 1 : Hello GED (US-01)`.
- Dans [docs/stories.md](stories.md), les tâches terminées sont cochées (`- [x]`) dans le même commit.

## Build
- Toujours utiliser le wrapper **`./mvnw`**, jamais `mvn` directement : il garantit la version de Maven, et le `mvn` de Homebrew peut utiliser une autre version de Java.
- Java **25**, Spring Boot **4.1**. Les versions sont fixées dans le [pom.xml](../pom.xml) parent.
