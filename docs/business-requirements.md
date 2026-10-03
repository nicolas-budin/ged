# Exigences métier — GED d'entreprise

| | |
|---|---|
| **Document** | Exigences métier (Business Requirements) |
| **Version** | 0.1 — brouillon |
| **Date** | 2026-10-02 |
| **Statut** | À valider par le sponsor |
| **Documents liés** | [Exigences utilisateurs](user-requirements.md) · [Spécifications techniques](technical-specifications.md) · [Backlog](stories.md) |

Les éléments marqués **[À compléter]** demandent une information de l'entreprise.

---

## 1. Objet du document

Ce document décrit **pourquoi** l'entreprise se dote d'une GED (gestion électronique des documents), **ce qu'elle doit permettre** d'un point de vue métier, et **les contraintes** à respecter. Il sert de référence pour arbitrer le périmètre et valider la V1.

Il ne décrit ni le fonctionnement détaillé des écrans (voir [Exigences utilisateurs](user-requirements.md)), ni la solution technique (voir [Spécifications techniques](technical-specifications.md)).

## 2. Contexte

- La GED servira **500 utilisateurs** et devra gérer **50 millions de documents**.
- L'entreprise est une **institution d'assurances sociales en Suisse**. Ses documents contiennent des **données personnelles sensibles** (notamment de santé), soumises au secret.
- L'entreprise gère des documents variés : factures, contrats, courriers, documents RH, procédures… Ils sont aujourd'hui stockés dans un **outil maison** (une base de données et un partage réseau).
- Les documents sont soumis à des **obligations légales** de conservation, de confidentialité et de traçabilité.
- **Pour raisons légales, aucun document ne peut être hébergé ni traité dans le cloud.** Tout doit rester sur l'infrastructure de l'entreprise.
- La GED doit pouvoir être **déployée chez plusieurs entreprises**, une instance par entreprise. Chacune doit pouvoir l'**adapter à ses propres besoins** sans développement spécifique.

## 3. Problèmes à résoudre

| # | Problème constaté |
|---|---|
| P1 | Retrouver un document prend du temps : classement hétérogène et pas de recherche dans le contenu des scans. |
| P2 | L'indexation des documents est manuelle et coûteuse. |
| P3 | Les circuits de validation (factures, contrats…) passent par email ou papier : pas de suivi, des retards, aucune preuve. |
| P4 | Les durées de conservation légales ne sont pas appliquées systématiquement. Risque de conserver trop longtemps, ou de supprimer trop tôt. |
| P5 | On ne sait pas toujours qui a consulté ou modifié un document. |
| P6 | Les applications de l'entreprise (ERP, numérisation…) ne peuvent pas échanger de documents automatiquement avec l'outil actuel. |
| P7 | L'outil actuel n'est pas conçu pour la volumétrie, ni pour être adapté à d'autres entreprises. |

## 4. Objectifs métier

| # | Objectif | Indicateur de succès |
|---|---|---|
| **BO-1** | Retrouver n'importe quel document en quelques secondes | 95 % des recherches répondent en moins de 0,5 s ; retrouver un document par un mot de son contenu, même scanné |
| **BO-2** | Réduire l'effort d'indexation | Part des documents pré-indexés automatiquement **[À compléter : cible, ex. 70 %]** |
| **BO-3** | Fiabiliser et accélérer les validations | 100 % des validations tracées ; délai moyen de validation **[À compléter : cible]** |
| **BO-4** | Garantir la conformité légale | Durées de conservation appliquées automatiquement ; journal d'audit infalsifiable ; aucune suppression possible sous legal hold |
| **BO-5** | Respecter la souveraineté des données | Aucun flux de données vers l'extérieur, vérifié techniquement |
| **BO-6** | Adapter la GED à chaque entreprise sans développement | Classes, champs, workflows et droits configurables par un administrateur fonctionnel |
| **BO-7** | Intégrer la GED au système d'information | Toutes les fonctions accessibles par une API documentée |
| **BO-8** | Tenir la volumétrie | Fonctionnement nominal avec **50 millions de documents** et 500 utilisateurs ; imports de masse possibles |

## 5. Périmètre

### 5.1 Dans le périmètre de la V1
- Dépôt, classement en dossiers, versions et téléchargement de documents
- **Champs d'indexation configurables** par l'entreprise
- Recherche plein texte (y compris les documents scannés, grâce à l'OCR) et **recherche à facettes**
- **Prévisualisation** des PDF, Word et autres formats bureautiques dans le navigateur
- **Classification et indexation automatiques** par une IA hébergée sur place
- **Corbeilles de travail** personnelles et de groupe (département), avec tri du courrier entrant et règles d'attribution automatiques
- **Workflows de validation** : 6 circuits fournis, et un éditeur pour en créer d'autres
- **Conformité** : durées de conservation, legal hold, éléments supprimés (restaurables), archivage inaltérable, journal d'audit
- **Droits d'accès** par utilisateur et par groupe, avec authentification unique (SSO) d'entreprise
- **API REST** publique pour les applications tierces
- **Import massif** de lots de documents

### 5.2 Hors périmètre de la V1
- **Reprise des documents de l'ancienne GED maison** (reportée ; l'import massif en sera la base)
- Édition en ligne des documents bureautiques
- Signature électronique
- Capture automatique (boîte mail, dossier surveillé, copieurs)
- Application mobile
- Mode multi-entreprise (SaaS) : une instance est déployée par entreprise

## 6. Parties prenantes

| Partie prenante | Intérêt principal |
|---|---|
| Direction / sponsor | Retour sur investissement, conformité, maîtrise des risques |
| Utilisateurs métier (comptabilité, RH, juridique, achats…) | Retrouver et déposer rapidement, moins de saisie |
| Valideurs / managers | Traiter les validations simplement, sans retard |
| Service courrier | Numériser et répartir rapidement le courrier entrant dans les bonnes corbeilles |
| Administrateurs fonctionnels | Adapter la GED sans dépendre des développeurs |
| Responsable conformité / DPO | Conservation légale, traçabilité, confidentialité |
| DSI / exploitation | Exploitation sur OpenShift, sécurité, performance, intégration au SI |
| Éditeurs d'applications internes (ERP…) | API stable et documentée |
| Product owner | Comprendre et valider le produit à chaque itération |

## 7. Exigences métier

Priorités : **M** = indispensable en V1 (*Must*), **S** = important (*Should*), **C** = souhaitable (*Could*).

| # | Exigence métier | Priorité | Objectif |
|---|---|---|---|
| **BR-01** | L'entreprise doit pouvoir conserver tous ses documents numériques dans un référentiel unique, organisé en dossiers. | M | BO-1, BO-8 |
| **BR-02** | Les documents doivent être versionnés, et l'historique des versions conservé. | M | BO-4 |
| **BR-03** | L'entreprise doit pouvoir définir elle-même ses **types de documents** et leurs **informations d'indexation**, sans développement ni interruption de service. | M | BO-6 |
| **BR-04** | Tout document doit être retrouvable par son contenu (y compris scanné) et par ses informations d'indexation, avec des filtres qui affichent le nombre de résultats. | M | BO-1 |
| **BR-05** | Un document doit pouvoir être consulté dans le navigateur sans être téléchargé, au minimum pour les formats PDF et Word. | M | BO-1 |
| **BR-06** | La GED doit proposer automatiquement le type et les informations d'indexation d'un document, avec une validation humaine quand elle n'est pas sûre. | S | BO-2 |
| **BR-07** | L'entreprise doit pouvoir faire valider des documents par des circuits définis (simples ou complexes), avec un suivi et une trace de chaque décision. | M | BO-3 |
| **BR-08** | Des circuits de validation standards doivent être utilisables immédiatement, sans paramétrage technique. | M | BO-3, BO-6 |
| **BR-09** | L'accès aux documents doit être restreint selon les personnes et les groupes, en s'appuyant sur les comptes d'entreprise existants (SSO). | M | BO-4 |
| **BR-10** | Les durées de conservation légales doivent être appliquées automatiquement, par type de document. | M | BO-4 |
| **BR-11** | Un document visé par un litige doit pouvoir être protégé contre toute suppression (legal hold). | M | BO-4 |
| **BR-12** | Toute action sur un document doit être tracée de façon infalsifiable, et la trace doit pouvoir être remise à un auditeur. | M | BO-4 |
| **BR-13** | Les documents archivés doivent être inaltérables. | M | BO-4 |
| **BR-14** | **Aucun document, texte extrait ou métadonnée ne doit quitter l'infrastructure de l'entreprise**, y compris pour l'IA. | M | BO-5 |
| **BR-15** | Les applications du SI doivent pouvoir déposer, rechercher et récupérer des documents, et être notifiées des événements, via une API documentée. | M | BO-7 |
| **BR-16** | L'entreprise doit pouvoir importer de grands lots de documents (millions) sans perturber les utilisateurs. | M | BO-8 |
| **BR-17** | La GED doit rester performante avec plusieurs millions de documents. | M | BO-1, BO-8 |
| **BR-18** | Un fichier infecté par un virus ne doit jamais être diffusé aux utilisateurs. | M | BO-4 |
| **BR-19** | La configuration (types, champs, workflows) doit pouvoir être transférée d'un environnement à un autre, et livrée sous forme de modèles métier. | S | BO-6 |
| **BR-20** | Les documents confidentiels doivent pouvoir être consultés avec un filigrane identifiant le lecteur. | C | BO-4 |
| **BR-21** | Les données sensibles (santé, assurances sociales) doivent être protégées conformément à la nLPD et au secret des assurances sociales : accès limité au besoin d'en connaître, chiffrement, journalisation des consultations. | M | BO-4, BO-5 |
| **BR-22** | La GED doit être remise en service en **6 heures au maximum** après un sinistre. | M | BO-4 |
| **BR-23** | Chaque collaborateur doit disposer d'une **corbeille de travail** où il voit les documents à traiter, et chaque département ou groupe de **ses propres corbeilles**. Un document peut être dans plusieurs corbeilles. Il y arrive par le tri du courrier, par des règles automatiques, par transmission ou par une étape de workflow, et il en sort une fois traité. Une corbeille ne donne aucun droit d'accès : les droits du dossier priment. | M | BO-1, BO-3 |

## 8. Contraintes

| # | Contrainte |
|---|---|
| **C-01** | **Souveraineté** : hébergement et traitements exclusivement sur l'infrastructure de l'entreprise. Aucun service cloud, aucun appel vers Internet. |
| **C-02** | **Plateforme** : OpenShift sur site. |
| **C-03** | **Authentification** : SSO d'entreprise via Keycloak (OIDC). |
| **C-04** | **Une instance par entreprise**, avec isolation complète des données. |
| **C-05** | **IA** : modèle hébergé sur place (Ollama + Gemma 4, déjà disponible). |
| **C-06** | **Langage backend** : Java / Spring Boot. |
| **C-07** | **Développement itératif** : petites itérations, chacune expliquée et validée par le product owner. |
| **C-08** | **Droit suisse, assurances sociales**. Textes à prendre en compte, liste **à valider par le service juridique** :<br>- **nLPD** (loi fédérale sur la protection des données, en vigueur depuis le 1.9.2023) : les données sur la santé sont des *données sensibles* ; sécurité des données, journalisation, analyse d'impact (AIPD) probablement requise ;<br>- **LPGA** (partie générale du droit des assurances sociales), notamment l'obligation de garder le secret, ainsi que les lois spéciales applicables (LAVS, LAI, LAA, LAMal, LPP… selon l'activité) ;<br>- **CO art. 958f** et **Olico** (tenue et conservation des livres) pour les pièces comptables : intégrité, preuve, lisibilité dans la durée. |
| **C-09** | **Conservation : 20 ans pour tous les documents**, sans durée spécifique par type. La possibilité de définir une durée par type reste disponible si le besoin apparaît. |
| **C-10** | **Reprise après sinistre** : remise en service en **6 heures** au maximum (RTO), avec au plus **6 heures** de données perdues (RPO). |
| **C-11** | **Navigateurs** : Chrome, Edge et Firefox, dernières versions. |
| **C-13** | **Langues : français, allemand et anglais**, pour l'interface, les libellés configurables, l'OCR et la recherche. |
| **C-12** | **Hébergement en Suisse** : découle de C-01 et C-08. L'infrastructure sur site, y compris le second site de sauvegarde, doit se trouver en Suisse **[à confirmer]**. |

## 9. Hypothèses

- Les utilisateurs et groupes existent déjà dans l'annuaire, et sont accessibles via Keycloak.
- L'infrastructure OpenShift dispose d'un stockage objet compatible S3 (ODF) et des ressources nécessaires : processeurs pour l'OCR, et serveur Ollama.
- Un registre d'images et des miroirs de dépendances internes sont disponibles.
- Un serveur SMTP interne est disponible pour les notifications.
- La durée de conservation est de **20 ans pour tous les documents**.
- **Organisation des documents : arborescence + métadonnées** (décision du product owner). Les dossiers servent au classement et aux droits, les métadonnées et la recherche à retrouver les documents, et les corbeilles au travail en cours. La notion métier de **« dossier assuré »** est portée par des **métadonnées** (n° AVS) et des vues de recherche, pas par l'arborescence.
- Sur 20 ans, les originaux sont conservés **tels quels**, sans conversion en PDF/A (décision Q8). Un rendu PDF de prévisualisation existe pour chaque document.

## 10. Risques métier

| # | Risque | Impact | Réponse |
|---|---|---|---|
| R1 | Débit de l'IA locale insuffisant pour des millions de documents | Indexation automatique lente | Traitement en tâche de fond ; IA non bloquante ; passage possible à vLLM |
| R2 | Qualité de l'OCR sur des scans médiocres | Recherche incomplète | Recommandation de numérisation à 300 DPI minimum ; mesure sur un échantillon réel |
| R3 | Paramétrage des droits incorrect | Fuite d'information interne | Refus par défaut ; audit des consultations ; recette des droits avec les métiers |
| R4 | Périmètre V1 large | Délais | Livraison par blocs démontrables ; priorisation MoSCoW |
| R5 | Report de la reprise de l'ancienne GED | Coexistence de deux outils | Import massif prêt en V1, reprise planifiée ensuite |

## 11. Points ouverts

| # | Question | Responsable |
|---|---|---|
| Q1 | ✅ **500 utilisateurs, 50 millions de documents.** Restent à préciser : utilisateurs simultanés en pointe, croissance annuelle et taille moyenne d'un document (pour le stockage) | Métier / DSI |
| Q2 | ✅ **20 ans pour tous les documents.** | Conformité |
| Q3 | Mode d'archivage inaltérable : strict (même un administrateur ne peut rien supprimer) ou gouverné | Conformité |
| Q4 | Cibles chiffrées des objectifs BO-2 (taux de pré-indexation par l'IA) et BO-3 (délai de validation) | Sponsor |
| Q5 | ✅ **RTO 6 h, RPO 6 h.** Reste à préciser : disponibilité attendue (heures de service, taux) | DSI |
| Q6 | Calendrier de la reprise de l'ancienne GED | Sponsor |
| Q7 | ✅ **Français, allemand et anglais.** | Sponsor |
| Q8 | ✅ **Pas de conversion en PDF/A** : les originaux sont conservés tels quels. | Conformité |
| Q9 | ✅ **Une analyse d'impact (AIPD) sera menée avec le DPO.** Reste à faire : validation par le service juridique de la liste des textes applicables (C-08) | Juridique / DPO |
| Q10 | Niveau d'accessibilité visé (ex. WCAG 2.1 AA, ou la norme suisse eCH-0059) | Sponsor |

## 12. Validation

| Rôle | Nom | Date | Visa |
|---|---|---|---|
| Sponsor | | | |
| Product owner | | | |
| Responsable conformité | | | |
| DSI | | | |
