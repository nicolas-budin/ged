-- Itération 2 : dossiers (US-02).
-- Règle : une migration appliquée n'est JAMAIS modifiée ; toute évolution = nouveau fichier V2__…, V3__…

CREATE TABLE folder (
    id          UUID         PRIMARY KEY,
    -- NULL = dossier racine. ON DELETE RESTRICT : la base elle-même refuse
    -- de supprimer un dossier qui a encore des sous-dossiers.
    parent_id   UUID         REFERENCES folder (id) ON DELETE RESTRICT,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,

    -- Deux dossiers frères ne peuvent pas porter le même nom.
    -- NULLS NOT DISTINCT : la règle s'applique aussi entre dossiers racines (parent_id NULL).
    CONSTRAINT uk_folder_parent_name UNIQUE NULLS NOT DISTINCT (parent_id, name)
);

-- Cette contrainte crée aussi l'index (parent_id, name) qui sert à lister les enfants
-- triés par nom, et à la pagination par curseur.
