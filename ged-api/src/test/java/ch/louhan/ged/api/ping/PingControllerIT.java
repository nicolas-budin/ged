package ch.louhan.ged.api.ping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import ch.louhan.ged.api.AbstractIntegrationTest;

/**
 * Test d'intégration : démarre toute l'application Spring et appelle les endpoints
 * via MockMvc (sans ouvrir de vrai port réseau).
 */
class PingControllerIT extends AbstractIntegrationTest {

    @Test
    void ping_renvoie_ok_et_la_version() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(RESPECTE_LE_CONTRAT)
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"));
    }

    @Test
    void health_renvoie_up() throws Exception {
        // Depuis l'itération 2, le health check vérifie aussi la connexion à PostgreSQL.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void le_contrat_et_swagger_ui_sont_servis_localement() throws Exception {
        mockMvc.perform(get("/api/openapi/ged-v1.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openapi: 3.0.3")));
        mockMvc.perform(get("/api/docs"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
