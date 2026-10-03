package ch.louhan.ged.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

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

    @Autowired
    protected MockMvc mockMvc;
}
