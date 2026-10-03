# Exigences utilisateurs — GED d'entreprise

| | |
|---|---|
| **Document** | Exigences utilisateurs (User Requirements) |
| **Version** | 0.1 — brouillon |
| **Date** | 2026-10-02 |
| **Statut** | À valider par le product owner et les référents métier |
| **Documents liés** | [Exigences métier](business-requirements.md) · [Spécifications techniques](technical-specifications.md) · [Backlog](stories.md) |

---

## 1. Objet du document

Ce document décrit **ce que chaque type d'utilisateur doit pouvoir faire** avec la GED, et **les qualités attendues** de son point de vue (rapidité, simplicité, sécurité).

- Chaque exigence (`UR-xx`) découle d'une exigence métier (`BR-xx`, voir [Exigences métier](business-requirements.md)).
- Elle est réalisée par une ou plusieurs user stories (`US-xx`, voir [Backlog](stories.md)).

Priorités : **M** = indispensable en V1, **S** = important, **C** = souhaitable.

## 2. Profils d'utilisateurs

| Profil | Description | Besoins clés |
|---|---|---|
| **Utilisateur** | Employé qui dépose, consulte et recherche des documents | Trouver vite, déposer sans effort, consulter sans télécharger |
| **Valideur** | Manager ou expert qui approuve des documents | Voir ses tâches dans sa corbeille, décider en un clic, être relancé |
| **Service courrier** | Collaborateurs qui numérisent le courrier entrant | Répartir chaque document dans la bonne corbeille, vite |
| **Administrateur fonctionnel** | Référent métier ou administrateur de la GED | Configurer types, champs, workflows et droits sans développeur |
| **Responsable conformité** | Responsable conformité, DPO, juriste | Conservation légale, legal hold, preuves d'audit |
| **Intégrateur** | Développeur d'une application tierce (ERP, numérisation) | API stable, documentée, fiable |
| **Exploitant** | Équipe d'exploitation OpenShift | Déployer, surveiller, dimensionner |

## 3. Exigences fonctionnelles

### 3.1 Accès et authentification

| # | Exigence | Profil | Prio | BR | US |
|---|---|---|---|---|---|
| **UR-01** | Je me connecte avec mon compte d'entreprise (SSO), sans mot de passe supplémentaire. | Tous | M | BR-09 | US-04 |
| **UR-02** | Une application tierce accède à la GED avec un compte de service aux droits limités. | Intégrateur | M | BR-15 | US-05 |
| **UR-03** | Je ne vois que les dossiers et documents auxquels j'ai droit. Un document interdit n'apparaît nulle part : listes, recherche, compteurs. | Utilisateur | M | BR-09 | US-13, US-28 |

### 3.2 Organisation et dépôt

| # | Exigence | Profil | Prio | BR | US |
|---|---|---|---|---|---|
| **UR-04** | Je crée, renomme et supprime des dossiers et sous-dossiers. | Utilisateur | M | BR-01 | US-02 |
| **UR-05** | Je dépose un ou plusieurs fichiers par glisser-déposer, avec une barre de progression. | Utilisateur | M | BR-01 | US-08, US-21 |
| **UR-06** | Je dépose des fichiers de plusieurs Go ; une coupure réseau ne m'oblige pas à tout recommencer. | Utilisateur | M | BR-01 | US-12 |
| **UR-07** | Je dépose une nouvelle version d'un document et je consulte ou télécharge les versions précédentes. | Utilisateur | M | BR-02 | US-10 |
| **UR-08** | Mon dépôt est immédiat : les traitements longs (OCR, IA, aperçu) se font ensuite, sans m'attendre. | Utilisateur | M | BR-17 | US-24 |
| **UR-09** | Un fichier infecté est bloqué et je suis prévenu. | Utilisateur | M | BR-18 | US-33 |

### 3.3 Indexation

| # | Exigence | Profil | Prio | BR | US |
|---|---|---|---|---|---|
| **UR-10** | Je choisis le type d'un document (ex. « Facture ») et je saisis ses informations dans un formulaire qui affiche **exactement** les champs de ce type. | Utilisateur | M | BR-03 | US-17, US-22 |
| **UR-11** | Le formulaire m'aide : listes déroulantes, formats de date et de montant, champs obligatoires signalés, erreurs expliquées sous chaque champ. | Utilisateur | M | BR-03 | US-22 |
| **UR-12** | La GED pré-remplit le type et les champs d'un document déposé. Je vois ce qui a été proposé par l'IA. | Utilisateur | S | BR-06 | US-34 |
| **UR-13** | Je revois les propositions incertaines de l'IA (proposition et aperçu côte à côte), et je les accepte, corrige ou rejette. | Utilisateur | S | BR-06 | US-35 |

### 3.4 Recherche et consultation

| # | Exigence | Profil | Prio | BR | US |
|---|---|---|---|---|---|
| **UR-14** | Je tape des mots et je retrouve les documents qui les contiennent, y compris les scans, avec les passages surlignés. | Utilisateur | M | BR-04 | US-26, US-30 |
| **UR-15** | J'affine les résultats en cochant des filtres (type, date, fournisseur…), avec le nombre de documents pour chaque valeur. | Utilisateur | M | BR-04 | US-27, US-30 |
| **UR-16** | Je trie les résultats et j'affiche les colonnes utiles pour le type de document. | Utilisateur | S | BR-04 | US-30 |
| **UR-17** | Je partage une recherche en copiant son lien. | Utilisateur | C | BR-04 | US-30 |
| **UR-18** | Je consulte un PDF, un Word, un Excel, un PowerPoint ou une image directement dans le navigateur : pages, zoom, recherche, rotation, plein écran. | Utilisateur | M | BR-05 | US-31 |
| **UR-19** | Un document de plusieurs centaines de pages s'ouvre immédiatement. | Utilisateur | M | BR-05, BR-17 | US-31 |
| **UR-20** | Quand j'ouvre un document depuis la recherche, les mots cherchés sont surlignés dans l'aperçu. | Utilisateur | S | BR-04 | US-31 |
| **UR-21** | Les documents confidentiels s'affichent avec un filigrane (mon nom, la date, « Confidentiel »). | Utilisateur | C | BR-20 | US-32 |
| **UR-22** | Je télécharge toujours le fichier **original**, jamais une copie convertie. | Utilisateur | M | BR-05 | US-09, US-31 |

### 3.5 Workflows de validation

| # | Exigence | Profil | Prio | BR | US |
|---|---|---|---|---|---|
| **UR-23** | Un document déposé dans un type associé à un circuit part automatiquement en validation. | Utilisateur | M | BR-07 | US-36 |
| **UR-24** | Mes tâches de validation et celles de mes groupes apparaissent **dans ma corbeille**, avec les autres documents à traiter. | Valideur | M | BR-07, BR-23 | US-36, US-37 |
| **UR-25** | J'approuve, je rejette avec un commentaire ou je délègue une tâche, depuis la liste ou la fiche du document. | Valideur | M | BR-07 | US-37 |
| **UR-26** | Je reçois un email quand une tâche m'est attribuée, et une relance si je dépasse le délai. | Valideur | M | BR-07 | US-37 |
| **UR-27** | Je vois où en est un document dans son circuit (schéma avec l'étape en cours). | Utilisateur | S | BR-07 | US-40 |
| **UR-28** | J'utilise immédiatement 6 circuits standards en choisissant seulement les groupes, seuils et délais. | Admin fonctionnel | M | BR-08 | US-38 |
| **UR-29** | Je dessine mes propres circuits dans un éditeur graphique, avec des conditions sur les champs du document, puis je les publie. | Admin fonctionnel | S | BR-07 | US-40 |
| **UR-30** | Je ne peux pas publier un circuit dangereux ou incohérent : la GED m'explique pourquoi. | Admin fonctionnel | M | BR-07 | US-39 |

### 3.6 Corbeilles de travail

| # | Exigence | Profil | Prio | BR | US |
|---|---|---|---|---|---|
| **UR-51** | Ma page d'accueil est **ma corbeille** : les documents que je dois traiter, ceux de mes corbeilles de groupe, avec compteurs, échéances et priorités. | Utilisateur | M | BR-23 | US-53, US-57 |
| **UR-52** | Dans une corbeille de groupe, je **prends** un document pour le traiter, et mes collègues voient que je m'en occupe. | Utilisateur | M | BR-23 | US-54 |
| **UR-53** | Je **transmets** un document à un collègue ou à un service, ou je l'envoie **en copie pour information**, avec un commentaire. Je **marque traité** un document, qui sort alors de ma corbeille. | Utilisateur | M | BR-23 | US-54 |
| **UR-54** | Je ne peux envoyer un document qu'à quelqu'un qui a le droit de le lire. La GED ne me propose que ces destinataires. | Utilisateur | M | BR-23, BR-21 | US-55, US-57 |
| **UR-55** | Je trie le courrier entrant numérisé : pour chaque document, je choisis sa classe et sa ou ses corbeilles, avec une proposition automatique. | Service courrier | M | BR-23 | US-57 |
| **UR-56** | Je définis des **règles d'attribution** (classe + conditions sur les champs → corbeille). Je suis alerté si une règle vise quelqu'un qui n'a pas le droit de lire. | Admin fonctionnel | M | BR-23 | US-56 |
| **UR-57** | Je crée et nomme (fr/de/en) les corbeilles des groupes et départements. | Admin fonctionnel | M | BR-23 | US-53 |

### 3.7 Administration

| # | Exigence | Profil | Prio | BR | US |
|---|---|---|---|---|---|
| **UR-31** | Je crée des types de documents et leurs champs depuis un écran, et la modification est visible **immédiatement** par les utilisateurs. | Admin fonctionnel | M | BR-03 | US-16, US-23 |
| **UR-32** | Un type peut hériter des champs d'un autre type. | Admin fonctionnel | S | BR-03 | US-19 |
| **UR-33** | Je gère des listes de valeurs (fournisseurs, services…). Je peux désactiver une valeur sans perdre l'historique. | Admin fonctionnel | M | BR-03 | US-18 |
| **UR-34** | Je modifie un champ (même son type) sans interrompre la recherche, et je reçois un rapport des documents à corriger. | Admin fonctionnel | S | BR-03 | US-29 |
| **UR-35** | J'exporte la configuration et je l'importe dans un autre environnement, avec un aperçu des différences. | Admin fonctionnel | S | BR-19 | US-20 |
| **UR-36** | J'accorde des droits (lecture, écriture, suppression, administration) à des personnes ou groupes sur un dossier, et ils s'appliquent à tout son contenu. | Admin fonctionnel | M | BR-09 | US-13 |
| **UR-37** | J'importe un lot de documents décrit par un fichier, après une vérification à blanc, et je suis l'avancement. | Admin fonctionnel | M | BR-16 | US-46, US-48 |

### 3.8 Conformité

| # | Exigence | Profil | Prio | BR | US |
|---|---|---|---|---|---|
| **UR-38** | Je définis une durée de conservation par type de document, et l'action à l'échéance (suppression ou archivage). Durée : **20 ans pour tous les documents**. | Conformité | M | BR-10 | US-41 |
| **UR-39** | Je place des documents sous legal hold (sélection, dossier ou résultat de recherche). Plus personne ne peut les supprimer, ni manuellement ni automatiquement. | Conformité | M | BR-11 | US-42 |
| **UR-40** | Je consulte l'historique de toutes les actions sur un document (qui, quoi, quand), **consultations comprises**. | Conformité | M | BR-12, BR-21 | US-14 |
| **UR-50** | Je sais qui a consulté les documents d'un assuré donné, pour répondre à une demande d'accès ou à un contrôle (nLPD). | Conformité | M | BR-21 | US-14, US-45 |
| **UR-41** | J'exporte le journal d'audit et je prouve qu'il n'a pas été modifié. | Conformité | M | BR-12 | US-15, US-45 |
| **UR-42** | Les documents archivés sont techniquement impossibles à modifier ou supprimer. | Conformité | M | BR-13 | US-44 |
| **UR-43** | Je restaure un document supprimé par erreur depuis les **éléments supprimés**. | Utilisateur | S | BR-01 | US-43 |

### 3.9 Intégration (API)

| # | Exigence | Profil | Prio | BR | US |
|---|---|---|---|---|---|
| **UR-44** | Je consulte une documentation interactive de l'API, à jour, hébergée par la GED. | Intégrateur | M | BR-15 | US-03 |
| **UR-45** | Toutes les fonctions de l'interface sont disponibles par l'API. | Intégrateur | M | BR-15 | toutes |
| **UR-46** | J'obtiens le schéma exact des champs d'un type de document, pour valider mes données avant envoi. | Intégrateur | M | BR-15 | US-19 |
| **UR-47** | Je peux rejouer un appel après une coupure sans créer de doublon, et je suis prévenu si le document a changé entre-temps. | Intégrateur | M | BR-15 | US-49 |
| **UR-48** | Mon application est notifiée des événements (document créé, classé, workflow terminé). | Intégrateur | S | BR-15 | US-51 |
| **UR-49** | J'importe des millions de documents sans ralentir les utilisateurs. | Intégrateur / Exploitant | M | BR-16 | US-47 |

## 4. Exigences de qualité perçues par les utilisateurs

| # | Exigence | Mesure |
|---|---|---|
| **UQ-01** | **Rapidité de la recherche** | 95 % des recherches en moins de 0,5 s, avec **50 millions de documents** et 500 utilisateurs |
| **UQ-02** | **Rapidité d'ouverture** | La première page d'un aperçu s'affiche en moins de 2 s, quelle que soit la taille du document (une fois l'aperçu généré) |
| **UQ-03** | **Dépôt sans attente** | Le document apparaît dans son dossier dès la fin du transfert |
| **UQ-04** | **Disponibilité de la recherche** | La recherche reste disponible pendant les changements de configuration et les imports |
| **UQ-05** | **Langues** | Interface, libellés des types, champs et listes de valeurs, et emails de notification en **français, allemand et anglais**. Chaque utilisateur choisit sa langue (par défaut, celle de son navigateur). La recherche trouve les documents quelle que soit leur langue (FR, DE, EN). |
| **UQ-06** | **Navigateurs** | Chrome, Edge et Firefox, dernières versions ✅ |
| **UQ-07** | **Accessibilité** | Navigation au clavier, contrastes suffisants ; niveau cible **[À confirmer : WCAG 2.1 AA ou eCH-0059 — Q10]** |
| **UQ-10** | **Secret et données sensibles** | Un utilisateur ne voit que les dossiers d'assurés dont il a besoin. Chaque consultation est tracée, et l'assuré ou l'auditeur peut savoir qui a consulté quoi (nLPD, LPGA) |
| **UQ-08** | **Messages compréhensibles** | Toute erreur explique la cause et l'action possible, sans jargon technique |
| **UQ-09** | **Confidentialité** | Aucune ressource n'est chargée depuis Internet ; l'utilisateur n'est jamais exposé à un service externe |

## 5. Parcours utilisateurs clés

### Parcours 1 : Du courrier entrant à la facture validée
1. Le service courrier numérise une facture. Elle arrive dans la corbeille « Courrier entrant ».
2. Quelques instants plus tard, le document est reconnu comme « Facture fournisseur » et les champs (n°, fournisseur, montant, échéance) sont pré-remplis. Une règle d'attribution propose la corbeille « Comptabilité — Fournisseurs ».
3. Le service courrier confirme d'un clic. La facture est classée dans son dossier et apparaît dans la corbeille de la comptabilité.
4. Une comptable la **prend**, vérifie les champs et corrige si besoin.
5. Le montant dépasse 5 000 CHF : le circuit « Validation selon un seuil » dépose une tâche dans la corbeille du directeur financier, qui reçoit un email.
6. Il approuve depuis **sa corbeille**. La facture passe au statut « Validée » et sort des corbeilles. Chaque étape figure dans l'historique.

### Parcours 2 : Retrouver un contrat
1. L'utilisateur tape « Dupont résiliation ».
2. Il coche les facettes « Contrat » et « 2025 ».
3. Il ouvre le résultat : l'aperçu s'affiche, avec « résiliation » surligné.
4. Il télécharge l'original signé.

### Parcours 3 : Créer un nouveau type de document
1. L'administrateur crée le type « Note de frais », qui hérite de « Document comptable ».
2. Il ajoute les champs « Collaborateur » (utilisateur), « Montant » (montant, obligatoire) et « Catégorie » (liste de valeurs, facette).
3. Il affecte le circuit « Double validation » (manager, puis comptabilité).
4. Sans redémarrage, les utilisateurs voient le nouveau type dans le formulaire et la facette « Catégorie » dans la recherche.

### Parcours 4 : Contrôle de conformité
1. Le responsable conformité place sous legal hold tous les documents du fournisseur X (depuis une recherche).
2. La rétention automatique ignore ces documents ; une tentative de suppression est refusée.
3. Il exporte l'audit de ces documents et la preuve d'intégrité pour l'avocat.

### Parcours 5 : Intégration d'un ERP
1. L'intégrateur obtient un compte de service avec les droits `documents:write`.
2. L'ERP dépose chaque facture émise avec ses métadonnées, en ajoutant une clé d'idempotence à chaque appel.
3. L'ERP est notifié par webhook quand la facture est validée.

## 6. Validation

| Rôle | Nom | Date | Visa |
|---|---|---|---|
| Product owner | | | |
| Référent métier | | | |
| Responsable conformité | | | |
