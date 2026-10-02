package ch.louhan.ged.api.ping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test d'intégration : démarre toute l'application Spring et appelle les endpoints
 * via MockMvc (sans ouvrir de vrai port réseau).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ping_renvoie_ok_et_la_version() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"));
    }

    @Test
    void health_renvoie_up() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
