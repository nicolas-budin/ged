# État du projet

> **À lire en premier par toute nouvelle session de travail**, juste après [CLAUDE.md](../CLAUDE.md).
> Ce fichier est **mis à jour à chaque fin d'itération, dans le même commit**.

Dernière mise à jour : 2026-10-03, après l'itération 3.

---

## 1. Où on en est

| | |
|---|---|
| **Itérations terminées** | 1 (Hello GED), 2 (Dossiers), 3 (Contrat d'abord), toutes commitées et poussées sur `origin/main` |
| **Dernier commit d'itération** | `2dfe663 Itération 3 : Contrat d'abord (US-03)` |
| **Prochaine itération** | **4 : Sécurité avec Keycloak** (US-04, US-05). Voir [stories.md](stories.md) |
| **Statut** | ⏸️ **en attente du feu vert du product owner.** Ne pas démarrer l'itération 4 sans son accord explicite. |

Pour vérifier l'état réel :
```bash
git log --oneline            # un commit par itération
grep -c "\- \[x\]" docs/stories.md   # tâches terminées
./mvnw verify                # doit être au vert (Docker démarré)
```

### Question à poser au product owner au début de l'itération 4
- **Accès à Swagger UI une fois l'authentification en place** : accessible sans connexion ? Proposition : oui en développement et sur le réseau interne, puisque Swagger UI ne montre que la documentation et aucune donnée.

---

## 2. Points reportés et dette technique

Ces points sont connus et volontairement reportés. Chacun doit être traité **au plus tard** à l'itération indiquée.

| Point | Détail | Itération cible |
|---|---|---|
| `created_by` sur `folder` | l'auteur d'un dossier n'est pas encore enregistré, faute d'utilisateurs | 4 (Keycloak) |
| Spectral dans la CI | le lint du contrat se lance aujourd'hui avec `npx`, donc via Internet en dev ; à brancher dans la CI via le miroir npm interne | 5 |
| Curseur de pagination générique | `Cursor` est propre aux dossiers (nom seul). Le généraliser, partagé et multi-valeurs (ex. date + id), quand une deuxième liste paginée apparaîtra | 6 (documents) |
| Messages de validation traduits | les messages Jakarta Validation sont en anglais (langue de la JVM). À traduire en fr/de/en selon `Accept-Language` | 16–17 (frontend) |
| Message d'erreur du motif de nom | il affiche l'expression régulière brute (`must match "^[^/\\]*…"`). À remplacer par un message lisible | 16–17 |
| Avertissement Mockito | « Mockito is currently self-attaching… » pendant les tests : sans impact aujourd'hui. Configurer Mockito comme agent Java quand il sera réellement utilisé | dès la première utilisation de Mockito |
| UUID v7 | les identifiants sont des UUID aléatoires (v4). Des UUID ordonnés dans le temps (v7) seraient meilleurs pour les index à 50 M de lignes | quand le volume le justifie (tests de charge, 36 au plus tard) |
| OpenAPI 3.1 | le contrat est en 3.0.3 (voir [ADR 0001](adr/0001-contrat-d-abord.md)). Réévaluer quand le générateur et le validateur prendront complètement en charge la 3.1 | à chaque montée de version des outils |

---

## 3. Décisions en attente du product owner

| Réf. | Sujet | Où c'est décrit |
|---|---|---|
| Q4 | objectifs chiffrés : taux de pré-indexation par l'IA, délai de validation | [business-requirements.md](business-requirements.md) § 11 |
| Q10 | niveau d'accessibilité (WCAG 2.1 AA ou eCH-0059) | idem |
| Q3 / T44.1 | mode Object Lock : COMPLIANCE ou GOVERNANCE | idem, et [stories.md](stories.md) T44.1 |
| T45.3 | format d'archivage des partitions d'audit (Parquet ou JSONL) | [stories.md](stories.md) |
| T50.1 | stockage des quotas d'appels (PostgreSQL ou cache local) | [stories.md](stories.md) |
| TQ-4 à TQ-10 | capacité d'Ollama, mTLS, coffre de secrets, opérateurs, second site, taille moyenne des documents, gestion des clés | [technical-specifications.md](technical-specifications.md) § 19 |

---

## 4. Pièges d'environnement

- **Toujours `./mvnw`**, jamais `mvn` : sur le poste du product owner, le `mvn` de Homebrew utilise **Java 27**, alors que le projet cible **Java 25**.
- **Docker Desktop** doit être démarré, pour les tests (Testcontainers) comme pour lancer l'application (`docker compose -f deploy/docker-compose.yml up -d --wait`).
- **Plusieurs sessions peuvent travailler sur le dépôt**, par exemple une session sur claude.ai qui pousse des corrections de documentation. Donc :
  - **`git fetch` avant tout commit**, et `git pull --ff-only` si la branche est en retard (ranger le travail en cours avec `git stash push -u` si nécessaire) ;
  - **ne jamais pousser sans que le product owner le demande** ;
  - **jamais de `git push --force` sur `main`**.
- Le code généré depuis le contrat est dans `ged-api/target/generated-sources/openapi/`. Il est recréé à chaque build : ne jamais le modifier ni le committer. Voir le [guide OpenAPI et Swagger](guides/openapi-swagger.md).

---

## 5. Comment reprendre le travail

1. Lire [CLAUDE.md](../CLAUDE.md), en particulier « Collaboration avec le product owner » et « Développement itératif ».
2. Lire ce fichier.
3. Lire [docs/conventions.md](conventions.md) avant d'écrire du code.
4. `git fetch && git status -sb` : vérifier que la branche est à jour.
5. `./mvnw verify` : vérifier que tout est au vert.
6. Annoncer la prochaine itération au product owner (stories visées, notion nouvelle, fichiers touchés), et **attendre son feu vert**.
