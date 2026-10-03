# GED — Gestion électronique des documents

GED d'entreprise, hébergée entièrement sur site (OpenShift). Le projet, ses contraintes et son plan de construction sont décrits dans [CLAUDE.md](CLAUDE.md). La documentation de référence se trouve dans [docs/](docs/).

## Prérequis

- **Java 25** (LTS), par exemple Temurin 25
- Pas besoin d'installer Maven : utiliser le wrapper `./mvnw`, qui télécharge Maven 3.9.16

Si plusieurs JDK sont installés, vérifier que le build utilise bien Java 25 :

```bash
./mvnw -v                      # doit afficher "Java version: 25…"
export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS, si nécessaire
```

## Versions retenues

| Composant | Version | Raison |
|---|---|---|
| Java | 25 | version LTS installée |
| Spring Boot | 4.1.1 | dernière version stable ; compatible avec Flowable 8, Spring Batch 6 et Spring AI 2, prévus dans les itérations suivantes |

Docker doit être démarré : PostgreSQL tourne dans un conteneur, en local comme pendant les tests (Testcontainers).

## Commandes

```bash
docker compose -f deploy/docker-compose.yml up -d   # démarre PostgreSQL (port 5432)
./mvnw verify                              # compile et lance tous les tests (Docker requis)
./mvnw -pl ged-api spring-boot:run         # démarre l'API sur http://localhost:8080
docker compose -f deploy/docker-compose.yml down    # arrête PostgreSQL (ajouter -v pour effacer les données)
```

## Vérifier que l'API répond

```bash
curl localhost:8080/api/v1/ping            # {"status":"ok","version":"0.1.0-SNAPSHOT"}
curl localhost:8080/actuator/health        # {"status":"UP", ...}

# Documentation interactive de l'API (Swagger UI, servie localement)
open http://localhost:8080/api/docs

# Dossiers
curl -X POST localhost:8080/api/v1/folders -H 'Content-Type: application/json' -d '{"name":"Comptabilité"}'
curl localhost:8080/api/v1/folders                     # dossiers racines
curl localhost:8080/api/v1/folders/<id>/children       # sous-dossiers (paginés : ?limit=&cursor=)
```

## Contrat de l'API

Le contrat [ged-v1.yaml](ged-api/src/main/resources/openapi/ged-v1.yaml) est la **source de vérité** de l'API : les interfaces Java des controllers et les objets JSON en sont générés à chaque build. Explications pas à pas : [guide OpenAPI et Swagger](docs/guides/openapi-swagger.md).

```bash
npx @stoplight/spectral-cli lint ged-api/src/main/resources/openapi/ged-v1.yaml   # lint du contrat
```

## Structure

```
ged/
├─ pom.xml        # POM parent : versions Java et Spring Boot, liste des modules
├─ deploy/        # docker-compose.yml (infrastructure locale)
└─ ged-api/       # API REST (Spring Boot)
```

Les autres modules (`ged-core`, `ged-worker`, `ged-importer`, `ged-web`) sont ajoutés au fil des itérations. Voir [docs/stories.md](docs/stories.md).
