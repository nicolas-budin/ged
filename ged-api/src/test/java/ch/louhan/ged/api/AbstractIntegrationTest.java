package ch.louhan.ged.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;

/**
 * Base commune des tests d'intégration : application complète + vrai PostgreSQL (Docker).
 *
 * <p>Toutes les classes de test qui en héritent partagent le même contexte Spring et donc
 * le même conteneur : il n'est démarré qu'une fois pour toute la suite de tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    /**
     * Test de contrat : vérifie que la requête ET la réponse respectent {@code openapi/ged-v1.yaml}
     * (route existante, paramètres, corps JSON, code HTTP déclaré, format des erreurs…).
     * Usage : {@code mockMvc.perform(...).andExpect(RESPECTE_LE_CONTRAT)}.
     */
    protected static final ResultMatcher RESPECTE_LE_CONTRAT = openApi().isValid("openapi/ged-v1.yaml");

    @Autowired
    protected MockMvc mockMvc;
}
