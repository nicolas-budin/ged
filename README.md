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

## Commandes

```bash
./mvnw verify                              # compile et lance tous les tests
./mvnw -pl ged-api spring-boot:run         # démarre l'API sur http://localhost:8080
```

## Vérifier que l'API répond

```bash
curl localhost:8080/api/v1/ping            # {"status":"ok","version":"0.1.0-SNAPSHOT"}
curl localhost:8080/actuator/health        # {"status":"UP", ...}
```

## Structure

```
ged/
├─ pom.xml        # POM parent : versions Java et Spring Boot, liste des modules
└─ ged-api/       # API REST (Spring Boot)
```

Les autres modules (`ged-core`, `ged-worker`, `ged-importer`, `ged-web`) sont ajoutés au fil des itérations. Voir [docs/stories.md](docs/stories.md).
