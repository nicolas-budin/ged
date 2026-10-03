# ADR 0013 : Flyway plutôt que Liquibase

- **Statut** : acceptée
- **Date** : 2026-10-02 (décision prise à l'itération 2, consignée le 2026-10-03)
- **Itération** : 2 (US-02)

## Contexte

Le schéma de la base PostgreSQL doit être versionné et appliqué automatiquement au démarrage de l'application, de façon identique en développement, en recette et en production. Deux outils standards existent dans l'écosystème Spring Boot : **Flyway** et **Liquibase**.

## Décision

Les migrations sont gérées par **Flyway**, sous forme de fichiers **SQL** (`src/main/resources/db/migration/V<n>__<description>.sql`). Une migration appliquée n'est jamais modifiée : toute évolution passe par un nouveau fichier.

## Raisons

1. **SQL PostgreSQL natif.** Le projet utilise de nombreuses fonctionnalités propres à PostgreSQL : `UNIQUE NULLS NOT DISTINCT` (dès la V1), `ltree` (droits hérités), le partitionnement par mois et les triggers (audit), les index GIN sur `JSONB` (métadonnées). Le format XML/YAML de Liquibase devrait de toute façon recourir à du SQL brut pour tout cela.
2. **Aucun besoin de portabilité.** Le principal atout de Liquibase est de générer le SQL de plusieurs bases (Oracle, SQL Server, MySQL…) à partir d'une description unique. La GED cible **uniquement PostgreSQL**.
3. **Simplicité et lisibilité.** Une migration Flyway se lit comme du SQL ordinaire, sans format supplémentaire à apprendre.
4. **Licence.** Flyway Community est sous licence **Apache 2.0**. Liquibase a adopté avec sa version 5 (2025) une licence plus restrictive, la *Functional Source License*. C'est un point de vigilance pour un produit déployé chez des clients, à faire confirmer par le service juridique si nécessaire.

## Alternatives écartées

- **Liquibase.** Ses atouts réels ne sont pas utiles ici :
  - le *rollback* intégré : en production, on corrige par une nouvelle migration plutôt que par un retour arrière ;
  - les préconditions et les *contexts* : remplaçables par des emplacements de migration par environnement ;
  - la comparaison entre deux bases (*diff*) : Flyway est la seule source du schéma.
- **Hibernate `ddl-auto: update`.** Exclu : aucun contrôle ni historique des changements de schéma, et un comportement imprévisible en production. Hibernate est configuré en `ddl-auto: validate`.

## Conséquences

- Les migrations sont en SQL PostgreSQL, relues comme du code.
- Le retour arrière d'une migration se fait par une **nouvelle** migration corrective. Le *undo* de Flyway est réservé à l'édition payante.
- Changer d'outil restera possible, mais deviendra plus coûteux à chaque migration ajoutée.
