package ch.louhan.ged.api.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;

import ch.louhan.ged.api.AbstractIntegrationTest;

/** Tests des endpoints /api/v1/folders contre un vrai PostgreSQL (US-02). */
class FolderControllerIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    /** Chaque test part d'une base vide : les tests ne dépendent pas les uns des autres. */
    @BeforeEach
    void viderLaBase() {
        jdbc.execute("TRUNCATE folder");
    }

    @Test
    void creer_un_dossier_racine_renvoie_201_et_son_adresse() throws Exception {
        mockMvc.perform(post("/api/v1/folders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Comptabilité"}"""))
                .andExpect(status().isCreated())
                .andExpect(RESPECTE_LE_CONTRAT)
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/folders/")))
                .andExpect(jsonPath("$.name").value("Comptabilité"))
                .andExpect(jsonPath("$.parentId").value(nullValue()));
    }

    @Test
    void creer_un_sous_dossier_puis_le_lister() throws Exception {
        UUID parent = create("Comptabilité", null);
        create("Fournisseurs", parent);

        mockMvc.perform(get("/api/v1/folders/{id}/children", parent))
                .andExpect(status().isOk())
                .andExpect(RESPECTE_LE_CONTRAT)
                .andExpect(jsonPath("$.items[*].name", contains("Fournisseurs")))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void deux_dossiers_freres_ne_peuvent_pas_avoir_le_meme_nom() throws Exception {
        UUID parent = create("RH", null);
        create("Contrats", parent);

        mockMvc.perform(post("/api/v1/folders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Contrats\", \"parentId\": \"" + parent + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(RESPECTE_LE_CONTRAT)
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:ged:problem:folder-name-already-used"))
                .andExpect(jsonPath("$.code").value("folder-name-already-used"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void deux_dossiers_racines_ne_peuvent_pas_avoir_le_meme_nom() throws Exception {
        create("Juridique", null);

        mockMvc.perform(post("/api/v1/folders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Juridique\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void le_meme_nom_est_autorise_dans_deux_dossiers_differents() throws Exception {
        UUID a = create("2025", null);
        UUID b = create("2026", null);
        create("Factures", a);
        create("Factures", b);   // ne doit pas lever d'erreur
    }

    @Test
    void creer_dans_un_parent_inexistant_renvoie_404() throws Exception {
        mockMvc.perform(post("/api/v1/folders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"X\", \"parentId\": \"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(RESPECTE_LE_CONTRAT)
                .andExpect(jsonPath("$.code").value("folder-not-found"));
    }

    @Test
    void un_nom_vide_ou_avec_slash_est_refuse() throws Exception {
        for (String name : new String[] {"", "   ", "a/b"}) {
            mockMvc.perform(post("/api/v1/folders").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"" + name + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("validation-failed"))
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }
    }

    @Test
    void renommer_un_dossier() throws Exception {
        UUID id = create("Brouillon", null);

        mockMvc.perform(patch("/api/v1/folders/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Définitif\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Définitif"));

        // La réponse ci-dessus est construite depuis l'objet en mémoire : elle ne prouve pas
        // l'écriture en base. On relit donc le dossier dans une nouvelle requête (nouvelle transaction)
        // ET directement en SQL, pour vérifier que JPA a bien écrit l'UPDATE sans appel à save().
        mockMvc.perform(get("/api/v1/folders/{id}", id))
                .andExpect(jsonPath("$.name").value("Définitif"));
        assertThat(jdbc.queryForObject("select name from folder where id = ?", String.class, id))
                .isEqualTo("Définitif");
    }

    @Test
    void supprimer_un_dossier_vide_puis_il_est_introuvable() throws Exception {
        UUID id = create("Temporaire", null);

        mockMvc.perform(delete("/api/v1/folders/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/folders/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void supprimer_un_dossier_non_vide_renvoie_409() throws Exception {
        UUID parent = create("Archives", null);
        create("2020", parent);

        mockMvc.perform(delete("/api/v1/folders/{id}", parent))
                .andExpect(status().isConflict())
                .andExpect(RESPECTE_LE_CONTRAT)
                .andExpect(jsonPath("$.code").value("folder-not-empty"));
    }

    @Test
    void la_pagination_par_curseur_parcourt_tous_les_dossiers_dans_l_ordre() throws Exception {
        for (String name : new String[] {"E", "B", "D", "A", "C"}) {
            create(name, null);
        }

        // Page 1 : A, B — avec un curseur pour la suite
        String page1 = mockMvc.perform(get("/api/v1/folders").param("limit", "2"))
                .andExpect(jsonPath("$.items[*].name", contains("A", "B")))
                .andReturn().getResponse().getContentAsString();
        String cursor1 = JsonPath.read(page1, "$.nextCursor");
        assertThat(cursor1).isNotNull();

        // Page 2 : C, D
        String page2 = mockMvc.perform(get("/api/v1/folders").param("limit", "2").param("cursor", cursor1))
                .andExpect(jsonPath("$.items[*].name", contains("C", "D")))
                .andReturn().getResponse().getContentAsString();
        String cursor2 = JsonPath.read(page2, "$.nextCursor");

        // Page 3 : E — dernière page, pas de curseur
        mockMvc.perform(get("/api/v1/folders").param("limit", "2").param("cursor", cursor2))
                .andExpect(jsonPath("$.items[*].name", contains("E")))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void une_limite_hors_bornes_un_curseur_ou_un_id_invalide_renvoient_400() throws Exception {
        mockMvc.perform(get("/api/v1/folders").param("limit", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        mockMvc.perform(get("/api/v1/folders").param("cursor", "%%%"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-cursor"));
        mockMvc.perform(get("/api/v1/folders/{id}", "pas-un-uuid"))
                .andExpect(status().isBadRequest())
                // Pas de RESPECTE_LE_CONTRAT ici : la requête est volontairement hors contrat.
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:ged:problem:bad-request"))
                .andExpect(jsonPath("$.code").value("bad-request"));
    }

    /** Crée un dossier via l'API et renvoie son identifiant. */
    private UUID create(String name, UUID parentId) throws Exception {
        String body = parentId == null
                ? "{\"name\": \"" + name + "\"}"
                : "{\"name\": \"" + name + "\", \"parentId\": \"" + parentId + "\"}";
        String json = mockMvc.perform(post("/api/v1/folders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }
}
