package ch.louhan.ged.api.ping;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint minimal pour vérifier que l'API répond (US-01).
 *
 * <p>Différence avec {@code /actuator/health} : {@code /ping} fait partie de l'API publique
 * versionnée ({@code /api/v1}) et renvoie la version déployée ; le health check est destiné
 * à OpenShift (probes) et vérifie aussi les dépendances (base de données, etc.).
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    private final BuildProperties buildProperties;

    // Injection par constructeur : Spring fournit BuildProperties, construit à partir
    // de META-INF/build-info.properties généré par le build Maven.
    public PingController(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse("ok", buildProperties.getVersion());
    }

    /** Réponse JSON : {@code {"status":"ok","version":"0.1.0-SNAPSHOT"}}. */
    public record PingResponse(String status, String version) {
    }
}
