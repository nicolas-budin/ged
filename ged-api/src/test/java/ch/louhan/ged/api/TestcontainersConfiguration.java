package ch.louhan.ged.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Conteneurs Docker utilisés par les tests d'intégration.
 *
 * <p>{@code @ServiceConnection} : Spring Boot lit l'adresse, l'utilisateur et le mot de passe
 * du conteneur démarré et configure automatiquement la connexion à la base ; inutile de
 * les écrire dans un fichier de configuration de test. Le conteneur est démarré une seule
 * fois et partagé par tous les tests qui utilisent le même contexte Spring.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        // Même version que deploy/docker-compose.yml
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
