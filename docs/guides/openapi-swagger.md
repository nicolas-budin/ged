# Guide : OpenAPI et Swagger UI dans la GED

Ce guide explique comment l'API de la GED est décrite, comment le code Java en est généré, et comment consulter et tester l'API avec Swagger UI. Il s'adresse à toute personne qui découvre le projet.

Pour aller plus loin :
- [ADR 0001 : Contrat d'abord](../adr/0001-contrat-d-abord.md) explique pourquoi on travaille ainsi ;
- [les conventions de code](../conventions.md), section « API REST » ;
- le contrat lui-même : [ged-v1.yaml](../../ged-api/src/main/resources/openapi/ged-v1.yaml).

---

## 1. OpenAPI en une phrase

OpenAPI est **le mode d'emploi de l'API, écrit dans un format que les machines savent lire**. On peut le comparer au plan d'un architecte : on le dessine d'abord, puis tout le monde construit à partir du même plan.

Ce plan, c'est le fichier [ged-v1.yaml](../../ged-api/src/main/resources/openapi/ged-v1.yaml). Il ne contient **aucun code**. Il décrit seulement les adresses de l'API, ce qu'il faut leur envoyer, et ce qu'on reçoit en retour (réponses normales et erreurs).

C'est la **source de vérité** de l'API : toute évolution de l'API commence par une modification de ce fichier, avant d'écrire du Java.

---

## 2. Le parcours d'une route, de bout en bout

On suit ici une seule route : la **création d'un dossier**, `POST /api/v1/folders`.

### Étape 1 : on décrit la route dans le YAML

```yaml
/folders:                          # l'adresse (le préfixe /api/v1 est dans « servers »)
  post:                            # la méthode HTTP
    operationId: createFolder      # le nom qu'aura la méthode Java générée
    requestBody:                   # ce que le client envoie
      content:
        application/json:
          schema:
            $ref: "#/components/schemas/CreateFolderRequest"
    responses:                     # tout ce que le client peut recevoir
      "201": ...                   # ✅ dossier créé
      "400": ...                   # ❌ données invalides
      "404": ...                   # ❌ dossier parent inexistant
      "409": ...                   # ❌ nom déjà pris
```

La forme du JSON envoyé est décrite plus bas, dans `components/schemas` :

```yaml
CreateFolderRequest:
  required: [name]                 # « name » est obligatoire
  properties:
    name:     { $ref: "#/components/schemas/FolderName" }   # longueur max, caractères interdits…
    parentId: { type: string, format: uuid }                # facultatif
```

`$ref` est un **renvoi** : « va voir la définition à cet endroit ». On décrit ainsi une seule fois les éléments réutilisés (le nom d'un dossier, les erreurs `BadRequest`/`NotFound`/`Conflict`, les paramètres de pagination…).

Les règles de validation (obligatoire, longueur maximale, motif, bornes) sont **écrites dans le contrat**. Le Java en est déduit.

### Étape 2 : le générateur écrit du Java

À chaque build (`./mvnw compile`), **openapi-generator** lit le YAML et écrit deux sortes de fichiers.

**Les objets JSON (DTO)**, par exemple `CreateFolderRequestDto` : un champ `name`, un champ `parentId`, et les annotations de validation tirées du YAML (`@NotNull`, `@Size`, `@Pattern`…). Le suffixe `Dto` les distingue des entités de base de données (`Folder`).

**Une interface par groupe de routes** (le `tag` du YAML), par exemple `FoldersApi` :

```java
public interface FoldersApi {

    @RequestMapping(method = RequestMethod.POST, value = "/folders")
    ResponseEntity<FolderDto> createFolder(@Valid @RequestBody CreateFolderRequestDto request);

    // … une méthode par route
}
```

La correspondance avec le YAML est directe :

| Dans le YAML | Dans le Java généré |
|---|---|
| `post` + `/folders` | `@RequestMapping(method = POST, value = "/folders")` |
| `operationId: createFolder` | la méthode `createFolder(...)` |
| `requestBody` → `CreateFolderRequest` | le paramètre `@RequestBody CreateFolderRequestDto` |
| réponse `201` → `Folder` | le type de retour `ResponseEntity<FolderDto>` |
| `maxLength`, `pattern`, `minimum`… | `@Size`, `@Pattern`, `@Min`… |

### Étape 3 : le développeur n'écrit que l'implémentation

```java
@RestController
public class FolderController implements FoldersApi {   // « je respecte le contrat »

    @Override
    public ResponseEntity<FolderDto> createFolder(CreateFolderRequestDto request) {
        Folder folder = service.create(request.getParentId(), request.getName().strip());
        return ResponseEntity.created(URI.create("/api/v1/folders/" + folder.getId()))
                .body(toDto(folder));
    }
}
```

Le controller ne contient **aucune annotation de route** : elles sont dans l'interface générée. `implements FoldersApi` est un engagement. Si une méthode manque ou si un type ne correspond pas, **le code ne compile pas**. Le contrat et le code ne peuvent donc pas diverger.

Le préfixe `/api/v1` (déclaré dans `servers` du contrat) est ajouté une seule fois, pour tous les controllers, par [WebConfig](../../ged-api/src/main/java/ch/louhan/ged/api/WebConfig.java).

### Étape 4 : un seul fichier, plusieurs usages

```
                    ged-v1.yaml
                         │
   ┌──────────┬──────────┼───────────┬──────────────┐
   ▼          ▼          ▼           ▼              ▼
 Java      Swagger UI   tests de   Spectral     client TypeScript
 généré    (/api/docs)  contrat    (lint)       du frontend (itération 16)
```

- **Java généré** : interfaces et DTO, comme décrit ci-dessus.
- **Swagger UI** : une page web de documentation, où l'on peut essayer chaque appel (voir § 4 et 5).
- **Tests de contrat** : dans les tests d'intégration, `.andExpect(RESPECTE_LE_CONTRAT)` vérifie que la requête **et** la réponse réelles correspondent au contrat (code HTTP déclaré, champs obligatoires, types, format des erreurs).
- **Spectral** : relit le contrat comme un correcteur (syntaxe, descriptions manquantes…).
- **Frontend** : son code d'appel à l'API sera généré à partir du même fichier. Le frontend et le backend parleront exactement la même langue.

### Pourquoi travailler ainsi

Sans contrat, l'API, c'est « ce que fait le code aujourd'hui ». Un champ renommé par mégarde casse une application cliente (un ERP, par exemple) sans que personne ne s'en aperçoive. Avec le contrat, toute modification de l'API passe par **un fichier lisible et relu**, et tout le reste suit automatiquement.

---

## 3. Où se trouvent les fichiers générés

Les `.java` générés et les `.class` compilés ne sont **pas au même endroit** :

```
ged-api/target/
├─ generated-sources/openapi/src/main/java/ch/louhan/ged/api/generated/   ← les .java générés (lisibles)
│     ├─ api/FoldersApi.java, PingApi.java
│     └─ model/CreateFolderRequestDto.java, FolderDto.java, …
│
└─ classes/ch/louhan/ged/api/generated/                                   ← les .class compilés (bytecode)
      └─ model/CreateFolderRequestDto.class, …
```

`./mvnw compile` enchaîne deux phases Maven :

1. **`generate-sources`** : le plugin openapi-generator lit `ged-v1.yaml` et écrit les `.java` dans `target/generated-sources/openapi/`. Il déclare ce dossier comme dossier de sources, au même titre que `src/main/java`.
2. **`compile`** : `javac` compile **tous** les `.java` (ceux de `src/main/java` et les générés) et range tous les `.class` ensemble dans `target/classes/`.

Règles :
- Le code généré n'est **jamais modifié à la main** : il serait écrasé au build suivant.
- Il n'est **jamais committé** : `target/` est ignoré par Git.
- Toujours utiliser le wrapper **`./mvnw`**, et pas un `mvn` installé autrement, qui peut utiliser une autre version de Java.

---

## 4. Dans VS Code

Tant que VS Code n'a pas indexé `target/generated-sources/`, il souligne en rouge les imports `ch.louhan.ged.api.generated…`. Ce n'est pas une erreur de compilation : le build Maven fonctionne. Il suffit de recharger le projet.

1. Ouvrir la **palette de commandes** : `⌘ Cmd` + `⇧ Shift` + `P`, ou le menu *Affichage → Palette de commandes…*.
   - La barre commence par **`>`**, ce qui signale le mode « commandes ».
   - Attention, `⌘ Cmd` + `P` (sans Shift) ouvre la **recherche de fichiers**. Taper alors `>` au début de la barre pour basculer en mode commandes.
2. Taper l'une de ces commandes :
   - **`Java: Clean Java Language Server Workspace`**, puis choisir « Reload and delete » ;
   - ou, plus léger, **`Maven: Reload project`**.

Ces commandes sont fournies par l'extension **« Extension Pack for Java »** de Microsoft (onglet Extensions : `⌘ Cmd` + `⇧ Shift` + `X`).

Une fois le projet rechargé, « Aller à la définition » fonctionne depuis le controller vers `FoldersApi`.

---

## 5. Comment Swagger UI est activé

Trois éléments, et aucune ligne de Java dédiée à Swagger.

### 5.1 La dépendance Maven : elle active tout

Dans [ged-api/pom.xml](../../ged-api/pom.xml) :

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

C'est un **« starter » Spring Boot**. Sa seule présence suffit : il se configure automatiquement au démarrage (auto-configuration). Il apporte :
- les fichiers de **Swagger UI** (HTML, JavaScript, CSS), **embarqués dans le JAR**, donc sans aucun CDN ;
- un controller qui sert ces fichiers ;
- un générateur de description de l'API, qui lit les controllers.

### 5.2 La configuration : où, et quoi afficher

Dans [application.yml](../../ged-api/src/main/resources/application.yml) :

```yaml
springdoc:
  api-docs:
    path: /api/openapi/generated     # (a)
  swagger-ui:
    path: /api/docs                  # (b)
    url: /api/openapi/ged-v1.yaml    # (c)
```

- **(b)** L'adresse où l'on ouvre Swagger UI. Par défaut, ce serait `/swagger-ui.html`.
- **(c)** Le point essentiel. Par défaut, springdoc affiche **sa propre** description de l'API, déduite du code Java. Ici, on lui demande d'afficher **notre contrat**. La documentation montre donc ce que l'API **promet**, conformément au principe du contrat d'abord.
- **(a)** La description déduite du code existe quand même, publiée à une adresse technique : springdoc n'active pas Swagger UI sans elle. Personne ne s'en sert.

### 5.3 La publication du contrat

Dans [WebConfig.java](../../ged-api/src/main/java/ch/louhan/ged/api/WebConfig.java) :

```java
registry.addResourceHandler("/api/openapi/**").addResourceLocations("classpath:/openapi/");
```

Swagger UI s'exécute **dans le navigateur** : il doit donc télécharger le YAML par HTTP. Cette ligne fait correspondre l'adresse `/api/openapi/ged-v1.yaml` au fichier `openapi/ged-v1.yaml` contenu dans le JAR.

### 5.4 Ce qui se passe à l'ouverture de la page

```
navigateur ──GET /api/docs───────────────────────────▶ springdoc : redirection (302) vers ↓
           ──GET /api/swagger-ui/index.html───────────▶ springdoc : page HTML + JS (embarqués)
           ──GET /api/openapi/generated/swagger-config▶ springdoc : « le contrat est à /api/openapi/ged-v1.yaml »
           ──GET /api/openapi/ged-v1.yaml─────────────▶ WebConfig : notre fichier YAML
           → Swagger UI dessine la page à partir du contrat
```

### 5.5 Sécurité : aucun appel sortant

Par défaut, Swagger UI envoie le contrat à un **validateur en ligne** (validator.swagger.io). Ce serait un appel vers Internet, interdit dans la GED. springdoc désactive ce validateur (`validatorUrl` vide). Le test `le_contrat_et_swagger_ui_sont_servis_localement`, dans [PingControllerIT](../../ged-api/src/test/java/ch/louhan/ged/api/ping/PingControllerIT.java), vérifie que tout est servi localement.

L'accès à Swagger UI une fois l'authentification en place sera décidé à l'itération 4. Swagger UI ne montre que la documentation, aucune donnée.

---

## 6. Utiliser Swagger UI

Swagger UI est servi **par l'API elle-même** : il n'est disponible que lorsque l'API tourne.

### Démarrer

```bash
# 1. Docker Desktop doit être démarré (icône de la baleine dans la barre de menu).

# 2. Démarrer PostgreSQL et attendre qu'il soit prêt :
docker compose -f deploy/docker-compose.yml up -d --wait
#    → « Container deploy-postgres-1  Healthy »

# 3. Démarrer l'API (laisser ce terminal ouvert) :
./mvnw -pl ged-api spring-boot:run
#    → attendre « Started GedApplication in … seconds »
```

Puis ouvrir dans le navigateur : **http://localhost:8080/api/docs**. La page affiche les sections **Ping** et **Folders**.

### Essayer un appel

1. Déplier **`POST /folders`**.
2. Cliquer sur **« Try it out »**.
3. Remplacer le JSON d'exemple par `{"name": "Mon premier dossier"}`.
4. Cliquer sur **« Execute »** : réponse **201**, avec le dossier créé.
5. Recommencer à l'identique : réponse **409**, au format `application/problem+json`, avec `"code": "folder-name-already-used"`.

Swagger UI affiche aussi la commande `curl` équivalente à chaque appel.

### Arrêter

- l'API : `Ctrl` + `C` dans son terminal ;
- PostgreSQL : `docker compose -f deploy/docker-compose.yml down`. Les données sont conservées. Pour les effacer : `down -v`.

### Dépannage

| Symptôme | Cause | Solution |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker Desktop n'est pas démarré | lancer Docker Desktop et attendre qu'il soit prêt |
| l'API s'arrête avec `Connection to localhost:5432 refused` | PostgreSQL n'est pas démarré ou pas prêt | `docker compose … up -d --wait`, puis relancer l'API |
| la page `/api/docs` ne répond pas | l'API n'est pas démarrée | voir « Démarrer » |
| `docker compose … ps` n'affiche pas `healthy` | PostgreSQL démarre encore | patienter quelques secondes |

---

## 7. Modifier l'API : la marche à suivre

1. **Modifier le contrat** [ged-v1.yaml](../../ged-api/src/main/resources/openapi/ged-v1.yaml) : nouvelle route, nouveau champ, nouvelle réponse d'erreur…
2. **Vérifier le contrat** avec Spectral, qui doit renvoyer 0 erreur :
   ```bash
   npx @stoplight/spectral-cli lint ged-api/src/main/resources/openapi/ged-v1.yaml
   ```
3. **Régénérer** : `./mvnw compile`. Le compilateur signale alors ce qu'il faut implémenter dans les controllers.
4. **Implémenter** la méthode dans le controller.
5. **Tester** : dans les tests d'intégration, ajouter `.andExpect(RESPECTE_LE_CONTRAT)` sur les requêtes valides, puis lancer `./mvnw verify`.

**Pièges du YAML**
- Une valeur qui contient « deux-points suivi d'un espace » (`: `) doit être **entre guillemets**, sinon le fichier est invalide.
- Dans un `pattern` entre apostrophes (`'...'`), le `\` est littéral : `'^[^/\\]+$'` interdit `/` et `\`.
- On ne retire ni ne renomme jamais un champ ou une route d'une version publiée (`/api/v1`) : ce serait un changement cassant pour les applications clientes.
