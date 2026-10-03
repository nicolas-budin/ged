# ADR 0001 : Contrat d'abord (OpenAPI 3.0.3, code généré)

- **Statut** : acceptée
- **Date** : 2026-10-03
- **Itération** : 3 (US-03)

## Contexte

La GED expose une API REST **publique**. L'interface web l'utilise sans aucun accès privilégié, tout comme les applications tierces (ERP, chaînes de numérisation). Cette API doit rester **stable** (aucun changement cassant dans `/api/v1`), **documentée** et **identique** pour tous ses consommateurs (backend, frontend, intégrateurs).

## Décision

1. Le contrat `ged-api/src/main/resources/openapi/ged-v1.yaml` est la **source de vérité**. Toute évolution de l'API commence par lui.
2. Le contrat est écrit en **OpenAPI 3.0.3**, et non 3.1.
3. Côté serveur, **openapi-generator** (générateur `spring`, `interfaceOnly`) génère au build :
   - les interfaces `XxxApi`, avec les routes, les paramètres et la validation ;
   - les DTO `XxxDto`.

   Les controllers implémentent ces interfaces. Le code généré n'est ni committé ni modifié.
4. Le contrat est **vérifié automatiquement** de trois façons :
   - lint **Spectral** (règles `spectral:oas`) ;
   - **tests de contrat** (`openapi-request-validator-mockmvc`) sur les requêtes valides des tests d'intégration ;
   - compilation : un controller qui ne respecte pas l'interface générée ne compile pas.
5. Les erreurs suivent la **RFC 9457** (`application/problem+json`), avec un champ `code` stable.
6. La documentation interactive est **Swagger UI**, servie par la GED elle-même (springdoc, ressources embarquées, validateur en ligne désactivé). Elle affiche le contrat, et non une description déduite du code.

## Pourquoi OpenAPI 3.0.3 plutôt que 3.1

En octobre 2026, le générateur Spring et le validateur de contrat ne prennent en charge OpenAPI 3.1 que partiellement. La version 3.0.3 est complètement prise en charge par toute la chaîne : générateur, validateur, Spectral et Swagger UI. Les apports de la 3.1 (JSON Schema complet, `type: [string, "null"]`) ne nous sont pas nécessaires : `nullable: true` suffit.

**À réévaluer** quand le générateur et le validateur prendront complètement en charge la 3.1. La migration est mécanique.

## Alternatives écartées

- **Code d'abord** (annotations dans le code Java, puis contrat déduit par springdoc) : le contrat dériverait au gré des modifications du code, sans relecture. Un changement cassant pourrait passer inaperçu.
- **MapStruct** pour la conversion entité ↔ DTO : inutile pour quelques champs. Les méthodes `toDto` écrites à la main restent lisibles. À reconsidérer si les conversions se multiplient.

## Conséquences

- Écrire le YAML avant le code ajoute une étape, mais elle oblige à réfléchir à l'API du point de vue de ceux qui l'utilisent.
- Les interfaces générées portent `@Validated`. `ApiExceptionHandler` doit donc convertir `ConstraintViolationException` en 400.
- Les chemins du contrat sont relatifs (`/folders`). Le préfixe `/api/v1` (`servers.url`) est ajouté par `WebConfig`.
- Le client TypeScript du frontend sera généré à partir du même contrat (itération 16).
